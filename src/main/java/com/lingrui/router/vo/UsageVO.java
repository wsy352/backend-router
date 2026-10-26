package com.lingrui.router.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 接收上游响应中的 token 用量
 * 保留基础用量和不同平台的缓存字段，供以后统一处理
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageVO {

    //输入总 token 数，包含缓存命中的部分
    @JsonProperty("prompt_tokens")
    private Long promptTokens;

    //输出 token 数
    @JsonProperty("completion_tokens")
    private Long completionTokens;

    //输入与输出的总 token 数
    @JsonProperty("total_tokens")
    private Long totalTokens;

    // deepseek 返回的缓存命中中输入 token 数
    @JsonProperty("prompt_cache_hit_tokens")
    private Long promptCacheHitTokens;

    // deepseek 返回的缓存未命中输入 token 数
    @JsonProperty("prompt_cache_miss_tokens")
    private Long promptCacheMissTokens;

    //嵌套的输入明细，包含 cached_tokens
    @JsonProperty("prompt_tokens_details")
    private PromptTokensDetailsVO promptTokensDetails;

}
