package com.lingrui.router.service;

import com.lingrui.router.entity.RouterWallet;
import com.lingrui.router.entity.RouterWalletTransaction;

/**
 * 钱包管理服务
 * 负责钱包初始化和钱包信息查询
 */
public interface RouterWalletService {
    /**
     * 获取用户钱包，不存在时创建余额钱包
     * @param userId 用户编号
     * @return 用户的钱包信息
     */
    RouterWallet getOrCreateWallet(Long userId);

    /**
     * 查询用户钱包，不创建新钱包
     * @param userId 用户编号
     * @return 已有的用户的钱包信息
     */
    RouterWallet getByUserId(Long userId);

    /**
     * 增加用户测试余额，并保存充值流水
     * @param userId 用户编号
     * @param businessId 充值业务编号，重试同一笔充值时保持不变
     * @param amountUnits 充值金额，单位为微元
     * @return 本次充值流水，重复提交时返回原流水
     */
    RouterWalletTransaction recharge(Long userId, String businessId, Long amountUnits);

    /**
     * 为一次请求预留预算，并保存预留流水
     * @param userId 用户编号
     * @param businessId 请求业务编号，同一次请求保持不变
     * @param amountUnits 预留金额，单位为微元
     * @return 本次预留流水
     */
    RouterWalletTransaction reserveBudget(Long userId, String businessId, Long amountUnits);

    /**
     * 全额释放指定请求的预留预算
     * @param userId 用户编号
     * @param businessId 原预留请求的业务编号
     * @return 释放流水，重复释放时返回原流水
     */
    RouterWalletTransaction releaseBudget(Long userId, String businessId);

    /**
     * 根据实际费用结算请求
     * @param userId 用户编号
     * @param businessId 原预留请求的业务编号
     * @param actualCostUnits 实际费用，单位为微元，允许为 0
     * @return 结算流水，重复提交相同费用时返回原流水
     */
    RouterWalletTransaction settleBudget(
            Long userId,
            String businessId,
            Long actualCostUnits
    );
}
