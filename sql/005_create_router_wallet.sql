USE nothing_router;

-- 用户钱包：保存可用余额和调用过程中预留的金额
--  user_id 与 router_user.id 逻辑关联，不建立物理外键
CREATE TABLE router_wallet(
    user_id BIGINT PRIMARY KEY COMMENT '钱包所属用户编号',
    balance_units BIGINT NOT NULL DEFAULT 0 COMMENT '可用余额，单位为微元',
    frozen_units BIGINT NOT NULL DEFAULT 0 COMMENT '预留余额，单位为微元',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;