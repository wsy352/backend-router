package com.lingrui.router.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 中转站身份验证配置
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "router.auth")
public class RouterAuthProperties {

    private String adminKey;
}
