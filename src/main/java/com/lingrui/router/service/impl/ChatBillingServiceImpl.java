package com.lingrui.router.service.impl;

import com.lingrui.router.config.ModelPriceProperties;
import com.lingrui.router.entity.ChatCallLog;
import com.lingrui.router.entity.RouterExplicitCache;
import com.lingrui.router.mapper.ChatCallLogMapper;
import com.lingrui.router.mapper.RouterExplicitCacheMapper;
import com.lingrui.router.service.BillingService;
import com.lingrui.router.service.ChatBillingService;
import com.lingrui.router.service.RouterExplicitCacheService;
import com.lingrui.router.service.RouterWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.function.IntUnaryOperator;

/**
 * 聊天资金流程实现
 * 使用短事务保证钱包操作与调用记录同时成功或同时回滚
 */
@Service
public class ChatBillingServiceImpl implements ChatBillingService {

    //钱包服务：负责余额查询，资金预留和预留流水
    @Autowired
    private RouterWalletService routerWalletService;

    //调用记录访问接口：保存请求身份，价格快照和处理状态
    @Autowired
    private ChatCallLogMapper chatCallLogMapper;

    //费用计算服务：根据数据库保存的价格快照重新核算结算金额
    @Autowired
    private BillingService billingService;

    //负责保存缓存创建记录
    @Autowired
    private RouterExplicitCacheMapper routerExplicitCacheMapper;

    /**
     * 预留资金并创建调用记录
     * 此方法只操作数据库，不向上游发送请求
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void reserveAndCreate(ChatCallLog chatCallLog) {

        validateReservation(chatCallLog);

        //同一次调用用 requestId 关联调用记录和钱包资金流水
        routerWalletService.reserveBudget(
                chatCallLog.getUserId(),
                chatCallLog.getRequestId(),
                chatCallLog.getReservedUnits()
        );

        LocalDateTime now = LocalDateTime.now();

        //上游未调用，用量和实际费用都还未知
        chatCallLog.setPromptTokens(null);
        chatCallLog.setCachedInputTokens(null);
        chatCallLog.setCompletionTokens(null);
        chatCallLog.setTotalTokens(null);
        chatCallLog.setCostUnits(null);
        chatCallLog.setErrorCode(null);
        chatCallLog.setDurationMs(null);

        chatCallLog.setStatus("PROCESSING");
        chatCallLog.setBillingStatus("RESERVED");

        chatCallLog.setCostCalculationStatus("NOT_CALCULATED");

        if(chatCallLog.getCreatedAt() == null){
            chatCallLog.setCreatedAt(now);
        }

        chatCallLog.setUpdatedAt(now);

        //插入失败必须抛出异常，使前面的钱包预留一起回滚
        int insertedRows = chatCallLogMapper.insert(chatCallLog);

        if(insertedRows != 1)
            throw new IllegalStateException("创建聊天调用记录失败");
    }

    /**
     * 创建缓存前的本地准备工作
     * 只操作数据库，不在事务里等待上游响应
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void reserveAndCreateCache(
            ChatCallLog chatCallLog,
            RouterExplicitCache cache
    ){
        //复用已有的身份，请求编号和预留金额检查
        validateReservation(chatCallLog);

        if(cache == null){
            throw new IllegalArgumentException("缓存记录不能为空");
        }

        //缓存记录必须与本次调用属于同一个用户和 key
        if(!Objects.equals(cache.getUserId(), chatCallLog.getUserId())
        || !Objects.equals(cache.getApiKeyId(), chatCallLog.getApiKeyId())){
            throw new IllegalArgumentException("缓存与调用记录的身份不一致");
        }

        //统一编号，后续能通过缓存编号定位创建账单
        if(!Objects.equals(
                cache.getCacheId(),
                chatCallLog.getRequestId()
        )){
            throw new IllegalArgumentException("缓存编号与创建请求编号不一致");
        }

        //两条记录必须描述同一个模型别名和渠道
        if(!Objects.equals(cache.getModelAlias(), chatCallLog.getModelAlias())
        || !Objects.equals(cache.getChannel(), chatCallLog.getChannel())){
            throw new IllegalArgumentException("缓存与调用记录的模型或渠道不一致");
        }

        //复用已有逻辑：预留钱包资金，再插入调用记录
        reserveAndCreate(chatCallLog);
        int insertedRows = routerExplicitCacheMapper.insertCreating(cache);

        if(insertedRows != 1){
            throw new IllegalArgumentException("创建显式缓存记录失败");
        }
    }

    /**
     * 检查执行资金预留所必须的参数
     * 这些参数有服务端安装，不直接信任客户端提供的身份或金额
     */
    private void validateReservation(ChatCallLog chatCallLog) {
        if(chatCallLog == null){
            throw new IllegalArgumentException("调用记录不能为空");
        }

        String requestId = chatCallLog.getRequestId();

        if(requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,64}")){
            throw new IllegalArgumentException("请求编号格式不合法");
        }

        if(chatCallLog.getUserId() == null
                || chatCallLog.getUserId() <= 0
                || chatCallLog.getApiKeyId() == null
                || chatCallLog.getApiKeyId() <= 0){
            throw new IllegalArgumentException("调用者身份信息不完整");
        }

        if(chatCallLog.getReservedUnits() == null
                || chatCallLog.getReservedUnits() <= 0){
            throw new IllegalArgumentException("预留资金单位数必须填写且大于零");
        }
    }

    /**
     * 结算成功调用
     * 钱包变动，结算流水和调用记录更新使用同一个事务
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void settleAndUpdate(ChatCallLog chatCallLog) {

        if(chatCallLog == null
                || chatCallLog.getRequestId() == null
                || !"SUCCESS".equals(chatCallLog.getStatus())){
            throw new IllegalArgumentException("只能结算具有请求编号的成功调用");
        }

        //用量不完整时不能再这里猜测费用，后续交给待处理流程
        if(chatCallLog.getPromptTokens() == null
                || chatCallLog.getCachedInputTokens() == null
                || chatCallLog.getCompletionTokens() == null) {
            throw new IllegalArgumentException("结算所需的用量不完整");
        }

        //先锁定请求，防止同一请求被两个线程同时处理
        ChatCallLog existingChatCallLog = chatCallLogMapper.selectByRequestIdForUpdate(chatCallLog.getRequestId());

        if(existingChatCallLog == null){
            throw new IllegalStateException("找不到本次调用的预留记录");
        }

        //请求编号不能关联到另一个用户或另一把 key
        if(!Objects.equals(existingChatCallLog.getUserId(),chatCallLog.getUserId())
                || !Objects.equals(existingChatCallLog.getApiKeyId(),chatCallLog.getApiKeyId())){
            throw new IllegalArgumentException("调用结果与预留记录的身份不一致");
        }

        //使用数据库中的价格快照，避免配置变化影响本次结算
        ModelPriceProperties modelPriceProperties = new ModelPriceProperties();

        modelPriceProperties.setVersion(existingChatCallLog.getPriceVersion());
        modelPriceProperties.setInputUnitsPerMillion(existingChatCallLog.getInputUnitsPerMillion());
        modelPriceProperties.setCachedInputUnitsPerMillion(existingChatCallLog.getCachedInputUnitsPerMillion());
        modelPriceProperties.setOutputUnitsPerMillion(existingChatCallLog.getOutputUnitsPerMillion());

        //计算服务同时检查负数，缓存量超过输入量等异常情况
        long actualCostUnits = billingService.calculateCost(
                chatCallLog.getPromptTokens(),
                chatCallLog.getCachedInputTokens(),
                chatCallLog.getCompletionTokens(),
                modelPriceProperties
        );

        long totalTokens = Math.addExact(
                chatCallLog.getPromptTokens(),
                chatCallLog.getCompletionTokens()
        );

        if(chatCallLog.getTotalTokens() != null
                && chatCallLog.getTotalTokens().longValue() != totalTokens){
            throw new IllegalArgumentException("总用量与输入输出之和不一致");
        }

        String billingStatus = existingChatCallLog.getBillingStatus();
        //已结算的相同结果直接返回，不能重复扣款
        if("SETTLED".equals(billingStatus)){
            if (!Objects.equals(existingChatCallLog.getCostUnits(), actualCostUnits)
                    || !Objects.equals(existingChatCallLog.getPromptTokens(), chatCallLog.getPromptTokens())
                    || !Objects.equals(existingChatCallLog.getCachedInputTokens(), chatCallLog.getCachedInputTokens())
                    || !Objects.equals(existingChatCallLog.getCompletionTokens(), chatCallLog.getCompletionTokens())
            ) {
                throw new IllegalStateException("该请求已按不同用量或费用结算");
            }
            return;
        }

        //已释放或状态异常的记录不能持续扣款
        if(!"RESERVED".equals(billingStatus) && !"PENDING".equals(billingStatus)){
            throw new IllegalStateException("不能结算已释放或状态异常的记录");
        }

        //钱包服务负责多退少补，并保证余额不能变成负数
        routerWalletService.settleBudget(
                chatCallLog.getUserId(),
                chatCallLog.getRequestId(),
                actualCostUnits
        );

        //在已锁定的数据库记录上填写结果，保留原身份和价格快照
        existingChatCallLog.setPromptTokens(chatCallLog.getPromptTokens());
        existingChatCallLog.setCachedInputTokens(chatCallLog.getCachedInputTokens());
        existingChatCallLog.setCompletionTokens(chatCallLog.getCompletionTokens());
        existingChatCallLog.setTotalTokens(totalTokens);
        existingChatCallLog.setCostUnits(actualCostUnits);
        existingChatCallLog.setBillingStatus("SETTLED");
        existingChatCallLog.setCostCalculationStatus("CALCULATED");
        existingChatCallLog.setDurationMs(chatCallLog.getDurationMs());
        existingChatCallLog.setErrorCode(null);
        existingChatCallLog.setStatus("SUCCESS");

        int updatedRows = chatCallLogMapper.updateResult(existingChatCallLog);

        if(updatedRows != 1)
            throw new IllegalStateException("更新聊天结算记录失败");
    }

    /**
     * 保存待处理结果，不扣款，不释放预留资金
     * 由外层在结算事务回滚完成后单独调用
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void markPending(ChatCallLog chatCallLog) {

        if(chatCallLog == null
                || chatCallLog.getRequestId() == null
                || chatCallLog.getRequestId().isBlank()){
            throw new IllegalArgumentException("调用记录和请求编号不能为空");
        }

        //只保存已结束的调用，不能提前处理仍在生成的请求
        String status = chatCallLog.getStatus();
        if(!"SUCCESS".equals(status) && !"FAILED".equals(status)){
            throw new IllegalArgumentException("只能保存已经结束的调用记录");
        }

        //与结算方法锁定同一条记录，避免互相覆盖资金状态
        ChatCallLog existingChatCallLog = chatCallLogMapper.selectByRequestIdForUpdate(chatCallLog.getRequestId());

        if(existingChatCallLog == null){
            throw new IllegalStateException("找不到本次调用的预留记录");
        }

        if(!Objects.equals(existingChatCallLog.getUserId(), chatCallLog.getUserId())
        || !Objects.equals(existingChatCallLog.getApiKeyId(), chatCallLog.getApiKeyId())){
            throw new IllegalArgumentException("调用结果与预留记录的身份不一致");
        }

        String billingStatus = existingChatCallLog.getBillingStatus();

        //已经处理完的请求，不能被迟到的异常通知改回待处理
        if("SETTLED".equals(billingStatus) || "RELEASED".equals(billingStatus)){
            return ;
        }

        //重复通知不覆盖第一次保存的结果
        if("PENDING".equals(billingStatus)){
            return;
        }

        if(!"RESERVED".equals(billingStatus)){
            throw new IllegalStateException("当前资金状态不能转为待处理");
        }

        //保存已知用量，未知部分仍保留 null
        existingChatCallLog.setPromptTokens(chatCallLog.getPromptTokens());
        existingChatCallLog.setCachedInputTokens(chatCallLog.getCachedInputTokens());
        existingChatCallLog.setCompletionTokens(chatCallLog.getCompletionTokens());
        existingChatCallLog.setTotalTokens(chatCallLog.getTotalTokens());
        existingChatCallLog.setDurationMs(chatCallLog.getDurationMs());
        existingChatCallLog.setStatus(status);
        existingChatCallLog.setErrorCode("FAILED".equals(status) ? chatCallLog.getErrorCode() : null);

        //此方法不核算费用，也不直接保存传入对象的金额
        existingChatCallLog.setCostUnits(null);

        boolean missingUsage = chatCallLog.getPromptTokens() == null
                || chatCallLog.getCachedInputTokens() == null
                ||chatCallLog.getCompletionTokens() == null;

        existingChatCallLog.setCostCalculationStatus(missingUsage ? "PENDING_USAGE" : "NOT_CALCULATED");

        existingChatCallLog.setBillingStatus("PENDING");

        int updatedRows = chatCallLogMapper.updateResult(existingChatCallLog);

        if(updatedRows != 1){
            throw new IllegalStateException("保存待处理调用记录失败");
        }
    }

    /**
     * 核对后释放失败请求的预留资金，并保存原因
     * 资金退回与原因记录必须在同一个事务中完成
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void releasePending(Long userId, String requestId, String reason){

        if(userId == null || userId <= 0 || requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,64}")){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户编号和请求编号不能为空或不合法"
            );
        }

        if(reason == null || reason.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "必须填写核对后的释放原因"
            );
        }

        String releaseReason = reason.strip();

        if(releaseReason.length() > 255)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "释放原因不能超过255个字符"
            );

        //与结算方法保持相同顺序：先锁定请求记录，再操作钱包
        ChatCallLog existingchatCallLog = chatCallLogMapper.selectByRequestIdForUpdate(requestId);

        if(existingchatCallLog ==null){
            throw new IllegalStateException("找不到待处理调用记录");
        }

        //防止用户编号与请求所属用户不一致
        if(!Objects.equals(existingchatCallLog.getUserId(), userId)){
            throw new IllegalArgumentException("请求不属于指定用户");
        }

        String billingStatus = existingchatCallLog.getBillingStatus();

        //重复提交释放操作空间时直接返回，不重复退钱
        if("RELEASED".equals(billingStatus)){
            if(!Objects.equals(
                    existingchatCallLog.getReleaseReason(),
                    releaseReason
            )){
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "该请求已释放，不能通过重复提交修改原因"
                );
            }

            return;
        }

        //只有已结束，待核对的失败调用才能通过当前入口释放
        if(!"PENDING".equals(billingStatus) || !"FAILED".equals(existingchatCallLog.getStatus())){
            throw new IllegalStateException("只能释放待处理的失败调用");
        }

        //钱包服务根据原预留流水确定金额，回复可用余额并记录释放流水
        routerWalletService.releaseBudget(
                existingchatCallLog.getUserId(),
                existingchatCallLog.getRequestId()
        );

        //保留原调用错误，已知用量和费用计算状态，只改变资金处理结果
        existingchatCallLog.setBillingStatus("RELEASED");

        //与释放状态一起保存，保证退款操作有核对依据
        existingchatCallLog.setReleaseReason(releaseReason);

        int updatedRows = chatCallLogMapper.updateResult(existingchatCallLog);

        if(updatedRows != 1){
            throw new IllegalStateException("更新资金释放状态失败");
        }
    }

    /**
     * 重试待处理请求的结算
     * 整个方法开启事务，检查状态和执行结算期间一直持有请求锁
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ChatCallLog retrySettlement(String requestId) {

        if(requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,64}")){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "请求编号必须为1至64位字母、数字、下划线或短横线"
            );
        }

        //从数据库读取真实记录，不能使用管理员提交的用量或单价
        ChatCallLog chatCallLog = chatCallLogMapper.selectByRequestIdForUpdate(requestId);

        if(chatCallLog == null){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "调用记录不存在"
            );
        }

        //已结算时直接返回，重复点击不会再次扣款
        if("SETTLED".equals(chatCallLog.getBillingStatus())){
            return chatCallLog;
        }

        //不能对仍在调用中的请求或已释放请求发起结算
        if(!"PENDING".equals(chatCallLog.getBillingStatus())){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "只有待处理请求可以重试结算"
            );
        }

        //当前自动结算流程只处理成功且用量完整的请求
        if(!"SUCCESS".equals(chatCallLog.getStatus())
        || chatCallLog.getPromptTokens() == null
        || chatCallLog.getCachedInputTokens() == null
        || chatCallLog.getCompletionTokens() == null){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "调用未成功或用量不完整就，不能直接重试结算"
            );
        }

        //当前方法已开启事务，这里复用结算逻辑，在同一个事务内执行
        settleAndUpdate(chatCallLog);

        //结算方法更新的是它读取的对象，因此重新查询得到最新结果
        ChatCallLog settledChatCallLog = chatCallLogMapper.selectByRequestIdForUpdate(requestId);

        if(settledChatCallLog == null
                ||!"SETTLED".equals(settledChatCallLog.getBillingStatus())) {
            throw new IllegalStateException("重试结算后状态异常");
        }

        return settledChatCallLog;

    }
}
