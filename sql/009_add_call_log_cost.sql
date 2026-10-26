-- 计算金额与钱包扣款是两个不同的阶段
ALTER TABLE nothing_router.chat_call_log
    ADD COLUMN cost_units BIGINT NULL COMMENT '计算费用，单位微元，NULL表示尚未算出',
    ADD COLUMN cost_calculation_status VARCHAR(32) NULL COMMENT '费用计算状态，历史记录为NULL';
