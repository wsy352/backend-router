-- 聊天调用开始时，最终耗时尚未确定
-- 允许 duration_ms 为空，调用结束后再写入实际耗时
ALTER TABLE nothing_router.chat_call_log
    MODIFY COLUMN duration_ms BIGINT NULL
        COMMENT '调用耗时，单位毫秒；尚未结束时为空';

-- 修正某个错误