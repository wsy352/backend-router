package com.lingrui.router.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 方舟 Responses API 创建前缀缓存后的响应
 * 仅用于接收上游数据，不直接返回给调用方
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArkPrefixCacheCreateVO {

    //上游响应编号，后续作为 previous_response_id 使用
    private String id;

    //上游返回的实际模型标识
    private String model;

    //上游处理状态，业务层需要确认是否成功完成
    private String status;

    //创建时刻，Unix 时间戳，单位为秒
    @JsonProperty("created_at")
    private Long createdAt;

    //过期时刻，Unix 时间戳，单位为秒
    @JsonProperty("expire_at")
    private Long expireAt;

    //创建缓存这次操作的用量
    private ArkResponseUsageVO usage;
}
