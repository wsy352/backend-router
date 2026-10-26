package com.lingrui.router.service.impl;

import com.lingrui.router.client.ArkCacheClient;
import com.lingrui.router.config.ArkProperties;
import com.lingrui.router.config.ExplicitCacheProperties;
import com.lingrui.router.config.ModelPriceProperties;
import com.lingrui.router.config.RouterRoutingProperties;
import com.lingrui.router.context.RouterAuthContext;
import com.lingrui.router.dto.ChatMessageDTO;
import com.lingrui.router.dto.ExplicitCacheCreateDTO;
import com.lingrui.router.entity.ChatCallLog;
import com.lingrui.router.entity.RouterExplicitCache;
import com.lingrui.router.mapper.RouterExplicitCacheMapper;
import com.lingrui.router.service.BillingService;
import com.lingrui.router.service.ChatBillingService;
import com.lingrui.router.service.RouterExplicitCacheService;
import com.lingrui.router.vo.ArkPrefixCacheCreateVO;
import com.lingrui.router.vo.ArkResponseUsageVO;
import com.lingrui.router.vo.ExplicitCacheVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.event.FocusEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 显式缓存业务实现
 */
@Slf4j
@Service
public class RouterExplicitCacheServiceImpl implements RouterExplicitCacheService {

    //负责读写显式缓存记录
    @Autowired
    private RouterExplicitCacheMapper routerExplicitCacheMapper;

    //显式缓存的有效期，请求规模和预算配置
    @Autowired
    private ExplicitCacheProperties explicitCacheProperties;

    //检查请求的模型别名是否允许使用方舟渠道
    @Autowired
    private RouterRoutingProperties routerRoutingProperties;

    //读取创建缓存时实际使用的方舟模型
    @Autowired
    private ArkProperties arkProperties;

    //复用整数费用计算及价格合法性检查
    @Autowired
    private BillingService billingService;

    //调用方舟创建前缀缓存
    @Autowired
    private ArkCacheClient arkCacheClient;

    //复用资金预留，实际结算和待处理账单逻辑
    @Autowired
    private ChatBillingService chatBillingService;

    /**
     * 创建缓存入口
     * 后续接入资金预留，上游创建和结果保存
     */
    @Override
    public ExplicitCacheVO create(
            ExplicitCacheCreateDTO request,
            RouterAuthContext authContext
    ){
        validateAuthContext(authContext);
        long ttlSeconds = validateCreateRequest(request);

        //服务端生成编号，同时关联缓存，调用记录和钱包流水记录
        String requestId = UUID.randomUUID().toString();

        ChatCallLog chatCallLog = buildCreateCallLog(
                request, authContext, requestId
        );

        RouterExplicitCache cache = buildCacheRecord(chatCallLog, ttlSeconds);

        //一个短事务内完成预留资金和两条记录的插入
        //失败时直接退出，不能直接调用上游
        chatBillingService.reserveAndCreateCache(chatCallLog, cache);

        long startNanos = System.nanoTime();

        ArkPrefixCacheCreateVO upstreamResponse;

        try{
            upstreamResponse = arkCacheClient.createPrefixCache(
                    request.getMessages(),
                    cache.getExpiresAt()
            );
        } catch(RuntimeException exception){
            chatCallLog.setDurationMs((System.nanoTime() - startNanos) / 1_000_000);

            //请求异常不一定表示上游没有创建成功，暂时保留资金待核对
            saveUnknownCreation(cache, chatCallLog, exception);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "缓存创建结果未确认，请勿立即重复创建，缓存编号：" + requestId
            );
        }

        chatCallLog.setDurationMs((System.nanoTime() - startNanos) / 1_000_000);

        //只有拿到完整，已完成的响应，才允许对外标记 READY
        if(upstreamResponse == null
        || !"completed".equals(upstreamResponse.getStatus())
        ||  upstreamResponse.getId() == null
        || upstreamResponse.getId().isBlank()
        || upstreamResponse.getId().length() > 256
        || upstreamResponse.getExpireAt() == null
        || upstreamResponse.getExpireAt() <= Instant.now().getEpochSecond()){

            saveUnknownCreation(
                    cache, chatCallLog, new IllegalStateException("上游未返回可用的缓存创建结果")
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "上游未确认缓存可用，缓存编号：" + requestId
                    );
        }

        //上游创建成功，与本地是否结算成功是两个不同的状态
        chatCallLog.setStatus("SUCCESS");

        copyCreateUsage(chatCallLog, upstreamResponse.getUsage());

        try{
            int updateRows = routerExplicitCacheMapper.markReady(
                    cache.getCacheId(),
                    cache.getApiKeyId(),
                    upstreamResponse.getId(),
                    upstreamResponse.getExpireAt()
            );

            if(updateRows != 1){
                throw new IllegalStateException("更新缓存创建结果失败");
            }
        } catch(RuntimeException exception){

            //保存本地结果失败，也不能把已经成功的上游调用当做免费
            savePendingCreation(chatCallLog);

            //保留关联编号，便于人工核对上游已创建的缓存
            log.error("缓存已在上游创建，但本地保存失败，cacheId={}，upstreamResponseId={}",
                    requestId, upstreamResponse.getId(), exception);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "缓存结果保存失败，请勿重复创建，缓存编号：" + requestId
            );
        }

        //更新内存对象，用于生成返回对象
        cache.setUpstreamResponseId(upstreamResponse.getId());
        cache.setExpiresAt(upstreamResponse.getExpireAt());
        cache.setStatus("READY");

        try{
            //使用实际用量和数据库中的价格快照结算
            chatBillingService.settleAndUpdate(chatCallLog);
        } catch(RuntimeException exception){
            log.error(
                    "缓存创建成功但结算失败，cacheId={}",
                    requestId, exception
            );

            savePendingCreation(chatCallLog);
        }

        return toVO(cache);
    }

    /**
     * 查询当前 key 创建的缓存
     */
    @Override
    public ExplicitCacheVO getOwned(
            String cacheId,
            RouterAuthContext authContext
    ){
        validateAuthContext(authContext);

        //当前编号不能为空，也不能超过数据库字段长度
        if(cacheId == null || cacheId.isBlank() || cacheId.length() > 64){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "缓存编号不能为空且不能超过64个字符"
            );
        }

        //key 编号取自服务端鉴权结果
        //SQL 同时匹配缓存编号和 key 编号
        RouterExplicitCache cache = routerExplicitCacheMapper.selectOwned(
                cacheId,
                authContext.getApiKeyId()
        );

        //额外核对用户归属
        //不存在和无权访问同一返回 404，避免泄露其他用户的缓存信息
        if(cache == null
        || !authContext.getUserId().equals(cache.getUserId())){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "缓存不存在"
            );
        }

        return toVO(cache);
    }

    /**
     * 检查缓存是否可以用于本次聊天
     * 校验失败时直接拒绝，不悄悄退回普通聊天
     */
    @Override
    public RouterExplicitCache requireUsable(
            String cacheId,
            String modelAlias,
            RouterAuthContext authContext
    ){
        validateAuthContext(authContext);

        if(!explicitCacheProperties.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "显式缓存功能尚未开启"
            );
        }

        if(cacheId == null || cacheId.isBlank()
                || cacheId.length() > 64) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "缓存编号不能为空且不能超过64个字符"
            );
        }

        //只查询当前 Key 拥有的缓存
        RouterExplicitCache cache = routerExplicitCacheMapper.selectOwned(
                        cacheId,
                        authContext.getApiKeyId()
                );

        if(cache == null
                || !authContext.getUserId().equals(cache.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "缓存不存在"
            );
        }

        //同一个缓存不能跨模型别名使用
        if(modelAlias == null
                || !modelAlias.equals(cache.getModelAlias())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "缓存所属模型与本次请求不一致"
            );
        }

        //创建中、失败、结果未知和已删除的缓存均不可使用
        if(!"READY".equals(cache.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "缓存尚不可用"
            );
        }

        //READY 不意味着永不过期，每次使用前都要检查
        if(cache.getExpiresAt() == null
                || cache.getExpiresAt() <= Instant.now().getEpochSecond()){
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "缓存已过期，请重新创建"
            );
        }

        //目前显式缓存只接入方舟
        if (!"ark".equals(cache.getChannel())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "缓存渠道暂不支持复用"
            );
        }

        //配置变更后，原渠道可能已从这个模型别名中移除
        List<String> channels =
                routerRoutingProperties.getModels().get(modelAlias);

        if (channels == null || !channels.contains(cache.getChannel())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "缓存渠道已不在当前模型的配置中"
            );
        }

        //缓存必须与创建时的实际模型匹配
        if (cache.getUpstreamModel() == null
                || !cache.getUpstreamModel().equals(arkProperties.getModel())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "上游模型配置已变化，请重新创建缓存"
            );
        }

        //缺少上游编号时，不能拼出有效的复用请求
        if (cache.getUpstreamResponseId() == null
                || cache.getUpstreamResponseId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "缓存记录缺少上游响应编号"
            );
        }

        return cache;
    }

    /**
     * 检查方法参数，并返回本次实际使用的有效期
     * 此方法不扣款，也不调用上游
     */
    private long validateCreateRequest(ExplicitCacheCreateDTO request){
        if(!explicitCacheProperties.isEnabled()){
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "显式缓存功能尚未开启"
            );
        }

        //先检查服务端配置，避免把配置错误当做调用方错误
        long defaultTtl = explicitCacheProperties.getDefaultTtlSeconds();
        long maxTtl = explicitCacheProperties.getMaxTtlSeconds();
        int maxPrefixChars = explicitCacheProperties.getMaxPrefixChars();
        Long reserveUnits = explicitCacheProperties.getReserveUnits();

        if(defaultTtl <= 0
                || maxTtl < defaultTtl
                || maxTtl > 7L * 24 * 60 * 60
                || maxPrefixChars <= 0
                || reserveUnits == null
                || reserveUnits <= 0){
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "显式缓存配置不完整或不合法"
            );
        }

        if(request == null
                || request.getModel() == null
                || request.getModel().isBlank()
                || request.getModel().length() > 128
        ){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "请提供有效的模型别名"
            );
        }

        //目前只有方舟实现了显式缓存创建
        List<String> channels = routerRoutingProperties.getModels().get(request.getModel());

        if(channels == null || !channels.contains("ark")){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "该模型别名未配置方舟显式缓存渠道"
            );
        }

        List<ChatMessageDTO> messages = request.getMessages();

        if(messages == null || messages.isEmpty()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "缓存前缀消息不能为空"
            );
        }

        //第一版明确只支持固定的 system 前缀
        long totalChars = 0;

        for(ChatMessageDTO message : messages){
            if(message == null
            || !"system".equals(message.getRole())
            || message.getContent() == null
            || message.getContent().isBlank()){
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "缓存前缀必须由非空的 system 消息组成"
                );
            }

            String content = message.getContent();
            totalChars += content.codePointCount(0, content.length());

            if(totalChars > maxPrefixChars){
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "缓存前缀超过允许的字符数"
                );
            }
        }

        long ttlSeconds = request.getTtlSeconds() == null
                ? defaultTtl
                :request.getTtlSeconds();

        if(ttlSeconds <= 0 ||ttlSeconds > maxTtl){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "缓存有效期必须大于零且不能超过配置上限"
            );
        }

        return ttlSeconds;
    }

    /**
     * 检查服务端传入的鉴权信息是否完整
     * 此方法不能替代 Controller 层对真实 Key 的鉴权
     */
    private void validateAuthContext(RouterAuthContext authContext){
        if(authContext == null
        || authContext.getUserId() == null
        || authContext.getUserId() <= 0
        || authContext.getApiKeyId() == null
        || authContext.getApiKeyId() <= 0){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请提供有效的访问秘钥"
            );
        }
    }

    /**
     * 将方舟返回的用量复制到统一调用记录
     * 未提供的字段保留 null,不能擅自当做零
     * 用量合法性由已有结算逻辑继续检查
     */
    private void copyCreateUsage(
            ChatCallLog chatCallLog, ArkResponseUsageVO usage){

        if(usage == null){
            return;
        }

        chatCallLog.setPromptTokens(usage.getInputTokens());
        chatCallLog.setCompletionTokens(usage.getOutputTokens());
        chatCallLog.setTotalTokens(usage.getTotalTokens());

        if(usage.getInputTokensDetails() != null){
            chatCallLog.setCachedInputTokens(
                    usage.getInputTokensDetails().getCachedTokens()
            );
        }
    }

    /**
     * 创建结果未确认时，分别尝试保存缓存状态和待处理账单
     * 一个保存失败，不能阻止另一个继续尝试
     */
    private void saveUnknownCreation(
            RouterExplicitCache cache,
            ChatCallLog chatCallLog,
            RuntimeException cause
    ){
        log.error(
                "缓存创建结果未确认，cacheId={}",
                cache.getCacheId(),
                cause
        );

        try {
            int updateRows = routerExplicitCacheMapper.markUnknown(
                    cache.getCacheId(),
                    cache.getApiKeyId()
            );

            if(updateRows != 1){
                throw new IllegalStateException("缓存未能转换为 UNKNOWN");
            }
        } catch(RuntimeException exception){
            log.error(
                    "保存缓存 UNKNOWN 状态失败，cacheId={}",
                    cache.getCacheId(),
                    exception
            );
        }

        //这里的 FAILED 表示本次调用未取得确认成功的结果
        //不代表已经确认上游没有创建缓存，也不能据此直接退款
        chatCallLog.setStatus("FAILED");
        chatCallLog.setErrorCode("CACHE_CREATE_UNKNOWN");

        savePendingCreation(chatCallLog);
    }

    /**
     * 保存待处理账单，保留预算供后续核对
     * 如果数据库仍不可用，原有 RESERVED 记录仍保留
     */
    private void savePendingCreation(ChatCallLog chatCallLog){

        try{
            chatBillingService.markPending(chatCallLog);
        } catch(RuntimeException exception){
            log.error(
                    "保存缓存待处理账单失败，需要核对预留记录，requestId={}",
                    chatCallLog.getRequestId(),
                    exception
            );
        }
    }

    /**
     * 组装缓存创建调用的账单
     * 这里只创建 java 对象，不操作数据库，不扣款
     */
    private ChatCallLog buildCreateCallLog(
            ExplicitCacheCreateDTO request,
            RouterAuthContext authContext,
            String requestId
    ){
        ModelPriceProperties price = explicitCacheProperties.getCreatePrice();

        //使用零用量检查价格是否完整，是否存在负数
        //这里只做计算，不会修改钱包，也不代表实际调用免费
        billingService.calculateCost(0L, 0L, 0L, price);

        ChatCallLog chatCallLog = new ChatCallLog();

        //同一个编号关联创建请求，缓存记录和资金流水
        chatCallLog.setRequestId(requestId);

        //身份只能来自鉴权结果
        chatCallLog.setUserId(authContext.getUserId());
        chatCallLog.setApiKeyId(authContext.getApiKeyId());
        chatCallLog.setModelAlias(request.getModel());
        chatCallLog.setChannel("ark");

        //创建前缀缓存使用非流式请求
        chatCallLog.setStream(false);

        //保存本次采用的价格快照，后续配置变化不影响这笔账单
        chatCallLog.setPriceVersion(price.getVersion());
        chatCallLog.setInputUnitsPerMillion(price.getInputUnitsPerMillion());
        chatCallLog.setCachedInputUnitsPerMillion(price.getCachedInputUnitsPerMillion());
        chatCallLog.setOutputUnitsPerMillion(price.getOutputUnitsPerMillion());

        //这里只记录预算，实际冻结由资金事务服务执行
        chatCallLog.setReservedUnits(explicitCacheProperties.getReserveUnits());

        //初始状态，创建时间和空用量由 reserveAndCreate 统一填写
        return chatCallLog;

    }

    /**
     * 组装本地缓存记录
     * 上游响应编号要等创建成功后才能编写
     */
    private RouterExplicitCache buildCacheRecord(
            ChatCallLog chatCallLog,
            long ttlSeconds
    ){
        String upstreamModel = arkProperties.getModel();

        //模型标识需要保存到数据库，调用前先检查
        if(upstreamModel == null
        || upstreamModel.isBlank()
        || upstreamModel.length() > 128){
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "方舟模型配置不合法"
            );
        }

        //使用 Unix 秒，与上游的过期时间字段保持一致
        long expiresAt = Math.addExact(
                Instant.now().getEpochSecond(),
                ttlSeconds
        );

        RouterExplicitCache cache = new RouterExplicitCache();

        //从账单复制身份和路由信息，确保两条记录一致
        cache.setCacheId(chatCallLog.getRequestId());
        cache.setUserId(chatCallLog.getUserId());
        cache.setApiKeyId(chatCallLog.getApiKeyId());
        cache.setModelAlias(chatCallLog.getModelAlias());
        cache.setChannel(chatCallLog.getChannel());
        cache.setUpstreamModel(upstreamModel);

        //上游尚未创建成功，不提前填写响应编号
        cache.setUpstreamResponseId(null);
        cache.setStatus("CREATING");
        cache.setExpiresAt(expiresAt);

        //数据库插入语句负责填写 created_at 和 updated_at
        return cache;
    }

    /**
     * 将数据库实体转换为对外响应
     * 只返回调用方需要的字段
     */
    private ExplicitCacheVO toVO(RouterExplicitCache cache){

        ExplicitCacheVO response = new ExplicitCacheVO();

        response.setCacheId(cache.getCacheId());
        response.setModel(cache.getModelAlias());
        response.setStatus(cache.getStatus());
        response.setExpiresAt(cache.getExpiresAt());

        return response;
    }
}
