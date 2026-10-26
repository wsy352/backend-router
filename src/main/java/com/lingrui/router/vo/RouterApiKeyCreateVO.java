package com.lingrui.router.vo;

import lombok.Data;

/**
 * API 秘钥创建结果
 */
@Data
public class RouterApiKeyCreateVO {

    private Long id;

    private Long userId;

    private String keyName;

    private String apiKey;
}
