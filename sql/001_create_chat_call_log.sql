USE nothing_router;

CREATE TABLE chat_call_log
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录编号',
    request_id  VARCHAR(64)  NOT NULL COMMENT '中转站生成的请求编号',
    model_alias VARCHAR(100) NOT NULL COMMENT '调用者使用的模型别名',
    channel     VARCHAR(50)  NOT NULL COMMENT '实际调用的上游渠道',
    stream      BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '是否为流式请求',
    prompt_tokens BIGINT NULL COMMENT '输入Token数，未知时为空',
    completion_tokens BIGINT NULL COMMENT '输出Token数，未知时为空',
    total_tokens BIGINT NULL COMMENT '总Token数，未知时为空',
    status      VARCHAR(20)  NOT NULL COMMENT '调用状态：SUCCESS或FAILED',
    error_code  VARCHAR(100) NULL COMMENT '失败时的错误分类',
    duration_ms BIGINT       NOT NULL COMMENT '调用耗时，单位毫秒',
    created_at  DATETIME(3)  NOT NULL COMMENT '请求开始时间',

    UNIQUE KEY uk_chat_call_log_request_id (request_id),
    KEY idx_chat_call_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;