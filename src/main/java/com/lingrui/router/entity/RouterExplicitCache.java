package com.lingrui.router.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 中转站显式缓存记录
 */
@Data
public class RouterExplicitCache {

    //数据库自增主键，用于内部定位记录
    private Long id;

    //中转站生成的缓存编号
    private String cacheId;

    //缓存所属用户的编号，逻辑关联 router_user 表
    private Long userId;

    //创建缓存的中转站秘钥编号
    private Long apiKeyId;

    //创建缓存时使用的模型别名
    private String modelAlias;

    //实际创建缓存的渠道
    private String channel;

    //创建时实际使用的上游模型
    private String upstreamModel;

    //上游创建成功后返回的响应编号，可以为空
    private String upstreamResponseId;

    //缓存生命周期状态
    private String status;

    //缓存过期时间
    private Long expiresAt;

    //本地缓存记录的创建时间
    private LocalDateTime createdAt;

    //最后一次更新的时间
    private LocalDateTime updatedAt;
}
