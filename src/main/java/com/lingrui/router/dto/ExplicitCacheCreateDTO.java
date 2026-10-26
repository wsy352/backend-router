package com.lingrui.router.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 创建显式缓存的请求参数
 */
@Data
public class ExplicitCacheCreateDTO {

    //调用方使用的模型别名，例如 doubao-chat
    private String model;

    //需要缓存的固定前缀消息
    //后续复用缓存时，只需另外提交新增的消息
    private List<ChatMessageDTO> messages;

    //缓存有效期，单位为秒
    //未填写时由业务层使用服务端默认配置
    @JsonProperty("ttl_seconds")
    private Long ttlSeconds;
}
