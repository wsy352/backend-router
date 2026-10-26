package com.lingrui.router.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话渠道绑定
 * 保持同一个中转站 key，模型别名和会话优先使用的渠道
 */
@Data
public class RouterSessionBinding {

    //绑定记录编号
    private Long id;

    //中转站秘钥编号，用于隔离不同 key 的会话
    private Long apiKeyId;

    //调用者使用的模型别名
    private String modelAlias;

    //调用者通过 X-Session-Id 请求头提供的会话编号
    private String sessionId;

    //优先使用的渠道标识，例如 deepseek、ark
    private String channel;

    //超过这个时间后，旧绑定不再作为有效路由依据
    private LocalDateTime expiresAt;

    //绑定创建时间
    private LocalDateTime createdAt;

    //绑定最后修改时间
    private LocalDateTime updatedAt;
}
