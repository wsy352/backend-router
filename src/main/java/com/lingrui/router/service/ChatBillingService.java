package com.lingrui.router.service;

import com.lingrui.router.entity.ChatCallLog;
import com.lingrui.router.entity.RouterExplicitCache;

/**
 * 聊天资金流程服务
 * 负责协调钱包资金变动和调用记录，使两在同一个事务中完成
 */
public interface ChatBillingService {

    /**
     * 为聊天请求预留资金，并保存处理中记录
     * 任一步骤失败时，数据库变更一起回滚
     * @param chatCallLog 已填写身份，渠道，价格快照和预算的调用记录
     */
    void reserveAndCreate(ChatCallLog chatCallLog);

    /**
     * 根据成功调用的实际用量结算资金，并更新调用记录
     * @param chatCallLog 包含完整归一化用量的成功调用结果
     */
    void settleAndUpdate(ChatCallLog chatCallLog);

    /**
     * 保存暂时无法结算的调用结果，保留预留资金等待后处理
     * @param chatCallLog 已结束的调用结果
     */
    void markPending(ChatCallLog chatCallLog);

    /**
     * 核对确认无需收费时后，释放请求失败的预留资金
     * 仅处理 PENDING 状态的失败调用，调用入口必须验证管理员权限
     * @param userId 请求所属用户编号
     * @param requestId 待处理请求编号
     * @param reason 管理员核对后释放资金的原因
     */
    void releasePending(Long userId, String requestId, String reason);

    /**
     * 使用数据库保存的用量和价格快照重试结算
     * @param requestId 待处理请求编号
     * @return 结算完成后的调用记录
     */
    ChatCallLog retrySettlement(String requestId);

    /**
     * 为创建显示缓存预留资金，并保存调用记录和缓存记录
     * 三项数据库操作在同一个事务中完成
     */
    void reserveAndCreateCache(
            ChatCallLog chatCallLog,
            RouterExplicitCache cache
    );
}


