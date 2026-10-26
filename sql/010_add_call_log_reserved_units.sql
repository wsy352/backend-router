-- 记录本次请求计划预留的预算；是否实际预留成功，以资金流水为准
ALTER TABLE nothing_router.chat_call_log
ADD COLUMN reserved_units BIGINT NULL COMMENT '本次请求计划预留的预算，单位为微元';
