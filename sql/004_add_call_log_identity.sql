USE nothing_router;

ALTER TABLE chat_call_log
    ADD COLUMN user_id BIGINT NULL COMMENT '调用用户编号，旧记录可能为空',
    ADD COLUMN api_key_id BIGINT NULL COMMENT '调用秘钥编号，旧记录可能为空',
    ADD INDEX idx_chat_call_log_user_id (user_id),
    ADD INDEX idx_chat_call_log_api_key_id (api_key_id);