package com.lingrui.router.vo;

import lombok.Data;

import java.util.List;

/**
 * 非流式聊天响应，包含相应标识，模型回答，用量信息
 */
@Data
public class ChatCompletionVO {
    private String id;//本次响应的唯一标识
    private String object;//本次响应的类型
    private long created;//本次响应的创建时间
    private String model;//本次响应所使用的模型
    private List<ChatChoiceVO> choices;
    private UsageVO usage;//本次响应的token用量
}
