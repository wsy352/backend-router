package com.lingrui.router.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上游渠道配置，当前阶段先只配一个deepseek渠道
 */
@Data
@Component
@ConfigurationProperties(prefix = "router.deepseek")
public class ChannelProperties {

    //上游渠道的地址
    private String baseUrl;

    //上游渠道的API Key
    private String apiKey;

    //上游渠道调用的模型
    private String model;

    //调用者使用的模型别名
    private String alias;

    //当前渠道模型的计费价格
    private ModelPriceProperties price;

    //调用者未指定 max_tokens 时使用的默认回答长度
    private Integer defaultMaxTokens = 128;

    //中转站允许调用者设置的最大回答长度
    private Integer maxAllowedTokens = 4096;

    //每次调用预留的金额，单位微元；只是预留预算，最终按实际用量结算
    private Long reserveUnits;

}
