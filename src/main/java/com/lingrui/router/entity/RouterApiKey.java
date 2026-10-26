package com.lingrui.router.entity;

/**
 * 用户的API秘钥记录
 */

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RouterApiKey {

    private Long id;

    private Long userId;

    //秘钥名称，用户自己起的备注，仅用于展示
    private String keyName;

    //秘钥的 SHA-256 哈希（64 位十六进制），验证时靠它查询比对
    private String keyHash;

    //秘钥前 12 位，用于在列表里展示给用户辨认，不参与验证
    private String keyPrefix;

    //状态：ENABLED 可用，DISABLED 已禁用
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
