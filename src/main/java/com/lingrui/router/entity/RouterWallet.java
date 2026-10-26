package com.lingrui.router.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户钱包
 * 保存用户的可用余额，以及尚未结算的请求预留金额
 * 所有金额统一使用1微元：1元 = 1,000,000微元
 */
@Data
public class RouterWallet {

    private Long userId;  //钱包所属用户编号

    private Long balanceUnits;  //可用余额

    private Long frozenUnits;  //预留余额

    private LocalDateTime createdAt;  //创建时间

    private LocalDateTime updatedAt;  //最后修改时间
}
