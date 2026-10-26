package com.lingrui.router.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 钱包流水资金
 * 记录一次业务操作造成的金额变化及操作后的钱包余额
 * 所有金额统一使用微元： 1元 = 1000000微元
 */
@Data
public class RouterWalletTransaction {

    private Long id; //流水编号，由数据库自动生成

    private Long userId; //这笔流水所属的用户编号

    private String businessId; //业务编号：关联充值操作或聊天请求

    private String transactionType; //流水类型：充值，预留，结算或释放预留金额

    private Long balanceChangeUnits; //可用余额变化量：正数表示增加，负数表示减少

    private Long frozenChangeUnits; //预留金额变化量：正数表示增加，负数表示减少

    private Long balanceAfterUnits; //本次操作完成后的可用余额

    private Long frozenAfterUnits; //本次操作完成后的预留金额

    private LocalDateTime createdAt; //流水创建时间
}
