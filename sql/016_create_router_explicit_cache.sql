USE nothing_router;

CREATE TABLE router_explicit_cache(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '内部记录编号',
    cache_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '中转站生成的缓存编号',
    user_id BIGINT NOT NULL COMMENT '所属用户编号',
    api_key_id BIGINT NOT NULL COMMENT '创建缓存的中转站秘钥编号',
    model_alias VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '创建时使用的模型别名',
    channel VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '创建缓存的渠道',
    upstream_model VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '创建时使用的上游模型',
    upstream_response_id VARCHAR(256) COLLATE utf8mb4_bin NULL COMMENT '上游创建成功后返回的响应编号',
    status VARCHAR(32) NOT NULL COMMENT 'CREATING、READY、FAILED、UNKNOWN、DELETED',
    expires_at BIGINT NOT NULL COMMENT '过期时间，Unix 秒',
    created_at DATETIME(3) NOT NULL COMMENT '记录创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '记录更新时间',

    UNIQUE KEY uk_explicit_cache_id (cache_id),
    KEY idx_explicit_cache_owner (api_key_id, created_at),
    KEY idx_explicit_cache_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;