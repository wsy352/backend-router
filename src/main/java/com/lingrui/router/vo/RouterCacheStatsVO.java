package com.lingrui.router.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 缓存用量和费用统计
 * 每条结果对应一个 key，渠道和模型别名
 */
@Data
public class RouterCacheStatsVO {

    //中转站秘钥编号，不返回秘钥原文
    private Long apiKeyId;

    //当前渠道标识，例如 deepseek, ark
    private String channel;

    //调用者使用的模型别名
    private String modelAlias;

    //成功调用的总请求数
    private Long successfulRequests;

    //输入量和缓存量均为有效的请求数
    private Long cacheSampleRequests;

    //缓存统计样本的输入 token 数
    private BigDecimal sampleInputTokens;

    //缓存统计样本的缓存命中 token 数
    private BigDecimal cachedInputTokens;

    //成功调用时，已计算且费用有效是请求数
    private Long settledRequests;

    //对应已结算请求的费用总额，单位为微元
    private BigDecimal settledCostUnits;

    /**
     * 缓存 token 命中率，范围为 0 至 1
     * 没有有效输入用量时返回 null,表示无法计算
     */
    public BigDecimal getCacheTokenHitRate(){

        if(sampleInputTokens == null
        || cachedInputTokens == null
        || sampleInputTokens.signum() <= 0){
            return null;
        }

        return cachedInputTokens.divide(
                sampleInputTokens,
                6,
                RoundingMode.HALF_UP
        );
    }
}
