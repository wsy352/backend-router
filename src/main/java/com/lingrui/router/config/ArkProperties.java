package com.lingrui.router.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 火山方舟渠道配置
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "router.ark")
public class ArkProperties {

    //调用者使用的模型别名
    private String alias;

    //上游渠道的地址
    private String baseUrl;

    //上游渠道的API Key
    private String apiKey;

    //上游渠道调用的模型
    private String model;

    //当前渠道模型的计费价格
    private ModelPriceProperties price;

    //调用者未指定 max_tokens 时使用的默认回答长度
    private Integer defaultMaxTokens = 128;

    //中转站允许调用者设置的最大回答长度
    private Integer maxAllowedTokens = 4096;

    //每次调用预留的金额，单位微元；只是预留预算，最终按实际用量结算
    private Long reserveUnits;
}
