package com.lingrui.router.service;

import com.lingrui.router.config.ModelPriceProperties;

/**
 * 费用计算服务
 * 根据统一后的 token 用量和指定价格计算费用，不修改钱包
 */
public interface BillingService {

    /**
     * 计算一次请求的费用
     * @param promptTokens 输入总 token 数，包含缓存命中部分
     * @param cachedInputTokens 输入总命中缓存的 token 数
     * @param completionTokens 输出 token 数
     * @param modelPriceProperties 本次请求使用的价格
     * @return 实际费用，单位为微元，整笔费用向上取整
     */
    long calculateCost(Long promptTokens,
                       Long cachedInputTokens,
                       Long completionTokens,
                       ModelPriceProperties modelPriceProperties);
}
