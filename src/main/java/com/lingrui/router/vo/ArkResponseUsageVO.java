package com.lingrui.router.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 方舟 Response API 返回的用量
 * 保留上游字段含义，后续转换为项目统一用量
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArkResponseUsageVO {

    //输入总量，包含命中缓存的部分
    @JsonProperty("input_tokens")
    private Long inputTokens;

    //输出总量
    @JsonProperty("output_tokens")
    private Long outputTokens;

    //输入与输出总量
    @JsonProperty("total_tokens")
    private Long totalTokens;

    //输入明细，其中 cached_tokens 表示缓存命中量
    @JsonProperty("input_tokens_details")
    private PromptTokensDetailsVO inputTokensDetails;
}
