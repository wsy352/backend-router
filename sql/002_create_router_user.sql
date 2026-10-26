USE nothing_router;

CREATE TABLE router_user
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户编号',
    username    VARCHAR(64)  NOT NULL COMMENT '唯一用户名',
    status      VARCHAR(20)  NOT NULL  DEFAULT 'ENABLED' COMMENT '用户状态:ENABLED 或 DISABLED',
    created_at  DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL COMMENT '最后修改时间',

    UNIQUE KEY uk_router_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
