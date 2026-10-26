package com.lingrui.router.dto;

import lombok.Data;

/**
 * 聊天请求中的一条信息，描述发消息的角色和聊天内容
 */
@Data
public class ChatMessageDTO {
    private String role;// 角色
    private String content;// 聊天内容
}
