package com.lingrui.router.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 滞留资金巡检配置
 * 巡检间隔由 BillingRecoveryTask 的 @Scheduled 直接读取 router.recovery.interval-ms
 */
@Data
@Component
@ConfigurationProperties(prefix = "router.recovery")
public class RecoveryProperties {

    //是否进入巡检，默认关闭，避免影响其它验证
    private boolean enabled = false;

    //停留超过多少秒才算滞留，必须大于零
    private long staleSeconds = 300L;

    //是否对用量完整的待处理账单自动重试结算
    private boolean autoRetryEnabled = false;

    //超过这个时长不再自动重试，避免坏数据反复重试
    private long maxRetryAgeSeconds = 86400L;

    //单次巡检最多处理多少条
    private int batchSize = 50;
}
