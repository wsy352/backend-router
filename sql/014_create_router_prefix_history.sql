USE nothing_router;
-- 请求前缀历史，记录哪些渠道成功处理过相同前缀
-- 只保存摘要，不保存聊天正文，不建立物理外键
CREATE TABLE router_prefix_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '历史记录编号',
    api_key_id BIGINT NOT NULL COMMENT '中转站秘钥编号',
    model_alias VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '模型别名',
    prefix_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定前缀的 SHA-256 摘要',
    channel VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '成功调用使用的渠道',
    last_success_at DATETIME(3) NOT NULL COMMENT '最后成功调用时间',
    expires_at DATETIME(3) NOT NULL COMMENT '路由线索失效时间',

    UNIQUE KEY uk_prefix_history(
                                api_key_id,
                                model_alias,
                                prefix_hash,
                                channel
        ),

    KEY idx_prefix_history_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
