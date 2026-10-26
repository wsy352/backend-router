package com.lingrui.router.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/**
 * 方舟 Responses API 的生成结果
 * 仅在内部使用，不直接返回给聊天接口调用方
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArkResponseVO {

    //本次响应编号
    private String id;

    //实际使用的上游模型
    private String model;

    //响应状态，例如 completed 或 incomplete
    private String status;

    //生成结果的创建时刻，Unix 秒
    @JsonProperty("created_at")
    private Long createdAt;

    //输出项目数组，后续提取 message 中的 output_text
    //保留原始结构，避免把推理内容误当成最终回答
    private JsonNode output;

    //生成未完整结束的原因
    @JsonProperty("incomplete_details")
    private JsonNode incompleteDetails;

    //上游返回的错误信息，不能直接透传给调用方
    private JsonNode error;

    //本次生成的用量，供归一化和计费使用
    private ArkResponseUsageVO usage;
}
