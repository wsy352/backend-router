USE nothing_router;
CREATE TABLE router_api_key(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'API密钥编号',

    user_id BIGINT NOT NULL COMMENT '所属用户编号',

    key_name VARCHAR(64) NOT NULL COMMENT 'API密钥名称，便于识别用途',

    key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'API密钥哈希值，用于快速查询',

    key_prefix VARCHAR(64) NOT NULL COMMENT 'API密钥前缀，用于快速查询',

    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'API密钥状态：ENABLED 或 DISABLED',

    created_at DATETIME(3) NOT NULL COMMENT '创建时间',

    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',

    UNIQUE KEY uk_router_api_key_hash (key_hash),

    KEY idx_router_api_key_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
