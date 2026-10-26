package com.lingrui.router.service.impl;

import com.lingrui.router.model.NormalizedUsage;
import com.lingrui.router.service.UsageNormalizationService;
import com.lingrui.router.vo.UsageVO;
import org.springframework.stereotype.Service;

/**
 * 用量归一化服务实现
 * 保留未知值，检查负数和矛盾数据，避免按错误用量计费
 */
@Service
public class UsageNormalizationServiceImpl implements UsageNormalizationService {

    @Override
    public NormalizedUsage normalize(String channel, UsageVO usageVO) {

        //未支持的渠道不能套用其他平台的规则
        if(!"deepseek".equals(channel) && !"ark".equals(channel)){
            throw new IllegalArgumentException("不支持的用量渠道：" + channel);
        }

        NormalizedUsage normalizedUsage = new NormalizedUsage();

        //没有返回 usage 时，保留未知状态，不伪造零用量
        if(usageVO == null){
            return normalizedUsage;
        }

        Long promptTokens = usageVO.getPromptTokens();
        Long completionTokens = usageVO.getCompletionTokens();
        Long totalTokens = usageVO.getTotalTokens();

        validateOptionalCount(promptTokens, "输入");
        validateOptionalCount(completionTokens, "输出");
        validateOptionalCount(totalTokens, "总");

        //两个平台都可能返回嵌套的缓存明细
        Long cachedInputTokens = null;

        if(usageVO.getPromptTokensDetails() != null){
            cachedInputTokens = usageVO.getPromptTokensDetails().getCachedTokens();
        }

        validateOptionalCount(cachedInputTokens, "缓存命中输入");

        if("deepseek".equals(channel)){
            cachedInputTokens = resolveDeepSeekCache(
                    usageVO,
                    promptTokens,
                    cachedInputTokens
            );
        }

        //缓存用量属于输入用量，不能超过输入总量
        if(promptTokens != null && cachedInputTokens != null && cachedInputTokens > promptTokens){
            throw new IllegalArgumentException("缓存命中输入量不能超过输入总量");
        }

        //输入和输出都已知时，可以精确计算总量
        if(promptTokens != null && completionTokens != null){
            long calculateTotal = Math.addExact(promptTokens, completionTokens);

            if(totalTokens != null && totalTokens.longValue() != calculateTotal){
                throw new IllegalArgumentException("返回的总用量与输入输出用量之和不一致");
            }

            totalTokens = calculateTotal;
        }

        normalizedUsage.setPromptTokens(promptTokens);
        normalizedUsage.setCachedInputTokens(cachedInputTokens);
        normalizedUsage.setCompletionTokens(completionTokens);
        normalizedUsage.setTotalTokens(totalTokens);

        return normalizedUsage;
    }

    /**
     * 统一的 deepseek 的缓存字段
     * 多种字段同时存在时，必须表达相同的缓存命中量
     */
    private Long resolveDeepSeekCache(
            UsageVO usageVO,
            Long promptTokens,
            Long nestedCachedTokens
    ) {
        Long hitTokens = usageVO.getPromptCacheHitTokens();
        Long missTokens = usageVO.getPromptCacheMissTokens();

        validateOptionalCount(hitTokens, "deepseek 缓存命中");
        validateOptionalCount(missTokens, "deepseek 缓存未命中");

        //两种字段都存在时，先检查是否一致
        if (hitTokens != null && nestedCachedTokens != null && !hitTokens.equals(nestedCachedTokens)) {
            throw new IllegalArgumentException("deepseek 缓存命中输入量与嵌套缓存命中输入量不一致");
        }

        Long cachedTokens = hitTokens != null ? hitTokens : nestedCachedTokens;

        //已知输入总量和未命中总量时，可以精确推导命中量
        if (promptTokens != null && missTokens != null) {
            if (missTokens > promptTokens) {
                throw new IllegalArgumentException("deepseek 缓存未命中输入量不能超过输入总量");
            }


            long calculatedHitTokens = promptTokens - missTokens;

            if (cachedTokens != null && cachedTokens.longValue() != calculatedHitTokens) {
                throw new IllegalArgumentException("deepseek 缓存命中输入量与计算的命中量不一致");
            }

            cachedTokens = calculatedHitTokens;
        }
        return cachedTokens;
    }

    /**
     * 缺失值允许保留，但已提供的数量不能是负数
     */
    private void validateOptionalCount(Long tokenCount, String name) {
        if (tokenCount != null && tokenCount < 0) {
            throw new IllegalArgumentException(name + "token 数不能是负数");
        }
    }
}
