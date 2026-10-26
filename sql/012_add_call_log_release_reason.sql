-- 保存管理员核对后释放预留资金的原因
ALTER TABLE nothing_router.chat_call_log
ADD COLUMN release_reason VARCHAR(255) NULL COMMENT '程序员核对后释放预留资金的原因';
