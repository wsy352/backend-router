package com.lingrui.router.model;

import lombok.Data;

/**
 * 中转站内部统一的 token 用量
 * null 表示未知，0 表示明确没有消耗，不能混为一谈
 */
@Data
public class NormalizedUsage {

    //输入总量，包含缓存命中的部分
    private Long promptTokens;

    //输入总量中命中缓存的部分
    private Long cachedInputTokens;

    //输出用量
    private Long completionTokens;

    //输入和输出的总用量
    private Long totalTokens;
}
