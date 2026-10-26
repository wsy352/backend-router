-- 调用是否成功与资金是否结算是两个独立状态
ALTER TABLE nothing_router.chat_call_log
    ADD COLUMN billing_status VARCHAR(32) NULL COMMENT '资金状态：RESERVED、SETTLED、RELEASED、PENDING',
    ADD COLUMN updated_at DATETIME(3) NULL COMMENT '调用记录最后更新时间';
