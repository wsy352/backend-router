USE nothing_router;

-- 钱包资金流水：记录每次操作对可用余额和预留金额的影响
--  user_id 与用户表逻辑关联
CREATE TABLE router_wallet_transaction(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '流水编号',
    user_id BIGINT NOT NULL COMMENT '用户编号',
    business_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务编号，同一次业务操作使用相同编号',
    transaction_type VARCHAR(20) NOT NULL COMMENT '交易类型：RECHARGE、RESERVE、SETTLE、RELEASE',
    balance_change_units BIGINT NOT NULL COMMENT '余额变化量，正数表示增加，负数表示减少',
    frozen_change_units BIGINT NOT NULL COMMENT '预留金额变化量，正数表示增加，负数表示减少',
    balance_after_units BIGINT NOT NULL COMMENT '操作后的可用余额',
    frozen_after_units BIGINT NOT NULL COMMENT '操作后的预留金额',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_wallet_transaction_business (user_id, business_id, transaction_type),
    KEY idx_wallet_transaction_user_id (user_id, id)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
