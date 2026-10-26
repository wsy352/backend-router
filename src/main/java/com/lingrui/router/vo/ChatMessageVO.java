package com.lingrui.router.vo;

import lombok.Data;

/**
 * 非流式聊天响应中的模型回复消息
 */
@Data
public class ChatMessageVO {
    private String role;//消息角色
    private String content;//模型回复的文本内容
}
