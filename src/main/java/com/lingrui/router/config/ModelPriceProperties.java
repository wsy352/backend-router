package com.lingrui.router.config;

import lombok.Data;

/**
 * 模型价格配置
 * 所有单价统一使用“每百万 token 对应多少微元”
 *  1 元 = 1,000，000 微元
 */
@Data
public class ModelPriceProperties {

    //价格版本：调整价格时同步修改，方便追溯
    private String version;

    //普通输入价格，单位：微元 / 百万 token
    private Long inputUnitsPerMillion;

    //缓存命中价格，单位：微元 / 百万 token
    private Long cachedInputUnitsPerMillion;

    //输出价格，单位：微元 / 百万 token
    private Long outputUnitsPerMillion;
}
