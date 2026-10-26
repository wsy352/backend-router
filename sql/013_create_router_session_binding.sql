USE nothing_router;

-- 会话渠道绑定：记录同一个 key，模型和会话优先使用的渠道
-- api_key_id 与秘钥表逻辑关联，不建立物理外键
CREATE TABLE router_session_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '绑定编号',
    api_key_id BIGINT NOT NULL COMMENT '中转站密钥编号',
    model_alias VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '模型别名',
    session_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '调用者提供的会话编号',
    channel VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '渠道表示',
    expires_at DATETIME(3) NOT NULL COMMENT '绑定失效时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    UNIQUE KEY uk_session_binding (
        api_key_id,
        model_alias,
        session_id
        ),
    KEY idx_session_binding_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
