-- 保存每次调用使用的价格，历史记录没有快照时保留 null
ALTER TABLE nothing_router.chat_call_log
    ADD COLUMN price_version VARCHAR(64) NULL COMMENT '本次调用使用的价格版本',
    ADD COLUMN input_units_per_million BIGINT NULL COMMENT '普通输入单价，微元/百万token',
    ADD COLUMN cached_input_units_per_million BIGINT NULL COMMENT '缓存命中单价，微元/百万token',
    ADD COLUMN output_units_per_million BIGINT NULL COMMENT '输出单价，微元/百万token';