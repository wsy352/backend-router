package com.lingrui.router.service;

import com.lingrui.router.vo.RouterApiKeyCreateVO;

/**
 * API 秘钥管理服务
 */
public interface RouterApiKeyService {

    /**
     * 为指定用户管理秘钥
     */
    RouterApiKeyCreateVO createApiKey(Long userId, String keyName);

    /**
     * 吊销指定用户的 API 秘钥
     */
    void revokeApiKey(Long userId, Long apiKeyId);
}
