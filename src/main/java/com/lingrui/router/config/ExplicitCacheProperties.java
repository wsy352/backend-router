package com.lingrui.router.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 中转站显式缓存配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "router.explicit-cache")
public class ExplicitCacheProperties {

    //创建流程全部接通后再开启
    private boolean enabled = false;

    //调用方未指定有效期时，默认保存一小时
    private long defaultTtlSeconds = 3600;

    //本项目默认最多允许保存一天
    private long maxTtlSeconds = 86400;

    //缓存前缀允许的最大字符数，控制请求体规模
    private int maxPrefixChars = 20000;

    //每次创建缓存预留的预算，单位为微元
    private Long reserveUnits;

    //创建缓存时的 token 单价，独立于普通聊天价格
    private ModelPriceProperties createPrice;
}
