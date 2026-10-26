package com.lingrui.router.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 返回给调用方的显式缓存信息
 * 不直接暴露数据库实体或上游响应对象
 */
@Data
public class ExplicitCacheVO {

    //中转站生成的缓存编号
    @JsonProperty("cache_id")
    private String cacheId;

    //创建缓存时使用的模型别名
    private String model;

    //缓存生命周期状态
    private String status;

    //缓存过期时刻，单位为秒
    @JsonProperty("expires_at")
    private Long expiresAt;
}
