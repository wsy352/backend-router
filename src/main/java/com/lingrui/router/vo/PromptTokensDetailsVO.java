package com.lingrui.router.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 上游返回的输入 token 明细
 * 当前处理文本调用，只接收缓存命中 token 数
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PromptTokensDetailsVO {

    //输入总量中命中缓存的部分，null 表示上游未提供
    @JsonProperty("cached_tokens")
    private Long cachedTokens;

}
