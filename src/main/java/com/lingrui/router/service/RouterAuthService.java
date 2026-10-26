package com.lingrui.router.service;

import com.lingrui.router.context.RouterAuthContext;

/**
 * 中转站身份验证服务
 */
public interface RouterAuthService {

    // 验证聊天访问秘钥,成功后返回当前请求的用户和秘钥编号
    RouterAuthContext validateChatKey(String authorization);

    // 验证管理员访问秘钥
    void validateAdminKey(String authorization);
}
