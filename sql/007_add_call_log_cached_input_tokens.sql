-- 保存缓存命中输入量，供后续缓存统计和费用核对使用
-- NULL 表示上游未提供或历史记录未知，0 表示明确没有命中缓存
ALTER TABLE nothing_router.chat_call_log
    ADD COLUMN cached_input_tokens BIGINT NULL COMMENT '输入总量中命中缓存的 token 数， NULL 表示未知'
    AFTER prompt_tokens