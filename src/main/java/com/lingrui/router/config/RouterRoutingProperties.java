package com.lingrui.router.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型路由配置
 * 描述每个模型别名允许使用的渠道，以及默认选择顺序
 */
@Data
@Component
@ConfigurationProperties(prefix = "router.routing")
public class RouterRoutingProperties {

    //是否使用前缀历史推荐渠道，默认开启
    private boolean prefixHistoryEnabled = true;

    //键是模型别名，值是允许使用的渠道列表
    //没有历史偏好时，默认选择列表中的第一个渠道
    private Map<String, List<String>> models = new LinkedHashMap<>();
}
