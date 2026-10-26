-- 检查一：钱包余额与资金流水累计是否一致
-- 前提：钱包初始金额为 0，所有资金变动均通过流水记录
-- 正常情况下不返回记录
USE nothing_router;
SELECT
    w.user_id,
    w.balance_units AS wallet_balance_units,
    COALESCE(t.balance_units, 0) AS ledger_balance_units,
    w.frozen_units AS wallet_frozen_units,
    COALESCE(t.frozen_units, 0) AS ledger_frozen_units
FROM router_wallet w
LEFT JOIN (
    SELECT
        user_id,
        SUM(balance_change_units) AS balance_units,
        SUM(frozen_change_units) AS frozen_units
    FROM router_wallet_transaction
    GROUP BY user_id
) t ON t.user_id = w.user_id
WHERE w.balance_units <> COALESCE(t.balance_units, 0)
OR w.frozen_units <> COALESCE(t.frozen_units, 0)
OR w.balance_units < 0
OR w.frozen_units < 0;

-- 检查二：是否存在资金流水，但对应钱包不存在
-- 逻辑关联没有数据库外键，需要主动检查
SELECT DISTINCT t.user_id
FROM router_wallet_transaction t
LEFT JOIN router_wallet w ON w.user_id = t.user_id
WHERE w.user_id IS NULL;

-- 检查三：冻结金额是否等于未结束预留的金额之和
-- 正常情况下不返回记录
SELECT
    w.user_id,
    w.frozen_units AS wallet_frozen_units,
    COALESCE(r.open_reserved_units, 0) AS open_reserved_units
FROM router_wallet w
LEFT JOIN (
    SELECT
        reserve_tx.user_id,
        SUM(reserve_tx.frozen_change_units) AS open_reserved_units
    FROM router_wallet_transaction reserve_tx
    WHERE reserve_tx.transaction_type = 'RESERVE'
    AND NOT EXISTS (
        SELECT 1
        FROM router_wallet_transaction terminal_tx
        WHERE terminal_tx.user_id = reserve_tx.user_id
        AND terminal_tx.business_id = reserve_tx.business_id
        AND terminal_tx.transaction_type IN ('SETTLE', 'RELEASE')
    )
    GROUP BY reserve_tx.user_id
) r ON r.user_id = w.user_id
WHERE w.frozen_units <>
      COALESCE(r.open_reserved_units, 0);

-- 检查四：已结算调用的记录费用是否与结算流水一致
-- 正常情况下不返回记录
SELECT
    c.request_id,
    c.user_id,
    c.cost_units AS recorded_cost_units,
    t.id AS settlement_transaction_id,
    -(
        CAST(t.balance_change_units AS DECIMAL(65, 0))
            + CAST(t.frozen_change_units AS DECIMAL(65, 0))
        ) AS ledger_cost_units
FROM chat_call_log c
         LEFT JOIN router_wallet_transaction t
                   ON t.user_id = c.user_id
                       AND t.business_id = c.request_id
                       AND t.transaction_type = 'SETTLE'
WHERE c.billing_status = 'SETTLED'
  AND (
    t.id IS NULL
        OR c.cost_units IS NULL
        OR c.cost_units < 0
        OR c.cost_units <> -(
        CAST(t.balance_change_units AS DECIMAL(65, 0))
            + CAST(t.frozen_change_units AS DECIMAL(65, 0))
        )
    );

-- 检查五：已结算费用是否符合用量和价格快照
-- DECIMAL 使用精确十进制运算，避免整数乘法溢出
-- 正常情况下不返回记录
WITH calculated_cost AS (
    SELECT
        request_id,
        user_id,
        cost_units,
        CASE
            WHEN prompt_tokens IS NULL
            OR cached_input_tokens IS NULL
            OR completion_tokens IS NULL
            OR input_units_per_million IS NULL
            OR cached_input_units_per_million IS NULL
            OR output_units_per_million IS NULL
            OR prompt_tokens < 0
            OR cached_input_tokens < 0
            OR completion_tokens < 0
            OR cached_input_tokens > prompt_tokens
            OR input_units_per_million < 0
            OR cached_input_units_per_million < 0
            OR output_units_per_million < 0
        THEN NULL
        ELSE CEILING(
                (
                    (
                        CAST(prompt_tokens AS DECIMAL(65, 0))
                        - CAST(cached_input_tokens AS DECIMAL(65, 0))
                    ) * input_units_per_million
                    + CAST(cached_input_tokens AS DECIMAL(65, 0))
                        * cached_input_units_per_million
                    + CAST(completion_tokens AS DECIMAL(65, 0))
                        * output_units_per_million
                ) * 0.000001
            )
        END AS expected_cost_units
        FROM chat_call_log
        WHERE billing_status = 'SETTLED'
)
SELECT
    request_id,
    user_id,
    cost_units AS recorded_cost_units,
    expected_cost_units

FROM calculated_cost
WHERE expected_cost_units IS NULL
OR cost_units IS NULL
OR cost_units <> expected_cost_units;

-- 检查六：同一笔业务是否同时存在结算和释放
-- 结算表示扣除实际费用，释放表示退回预留资金
-- 同一笔预留不能通过这两种方式结束
-- 正常情况下不返回记录
SELECT
    user_id,
    business_id,
    SUM(
        CASE
            WHEN transaction_type = 'SETTLE' THEN 1
            ELSE 0
        END
    ) AS settlement_count,
    SUM(
        CASE
            WHEN transaction_type = 'RELEASE' THEN 1
            ELSE 0
        END
    ) AS release_count
FROM router_wallet_transaction
WHERE transaction_type IN ('SETTLE', 'RELEASE')
GROUP BY user_id, business_id
HAVING settlement_count > 0
AND release_count > 0;





