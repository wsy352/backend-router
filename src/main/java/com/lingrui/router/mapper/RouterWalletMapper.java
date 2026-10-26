package com.lingrui.router.mapper;

import com.lingrui.router.entity.RouterWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 钱包数据访问接口
 * 负责创建钱包和根据用户查询钱包
 */
@Mapper
public interface RouterWalletMapper {

    /**
     * 创建钱包
     * @param routerWallet 钱包信息，初始余额和预留金额均为零
     * @return 插入的行数，成功时为1
     */
    int insert(RouterWallet routerWallet);

    /**
     * 根据用户编号查询钱包
     * @param userId 用户编号
     * @return 钱包信息，不存在时返回null
     */
    RouterWallet selectByUserId(@Param("userId") Long userId);

    /**
     * 查询并锁定用户钱包
     * 必须在事务中调用，锁在事务结束时释放
     * @param userId 用户编号
     * @return 钱包信息，不存在时返回null
     */
    RouterWallet selectByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 增加可用余额
     * @param userId 用户编号
     * @param amountUnits 增加的金额，单位为微元，必须为正数
     * @return 更新行数，成功时为 1
     */
    int increaseBalance(@Param("userId") Long userId, @Param("amountUnits") Long amountUnits);

    /**
     * 从可用余额中预留请求预算
     * 同时减少可用余额，增加预留金额
     * @param userId 用户编号
     * @param amountUnits 预留金额，单位为微元，必须为正数
     * @return 更新行数，成功为 1，不满足更新条件为 0
     */
    int reserveBalance(
            @Param("userId") Long userId,
            @Param("amountUnits") Long amountUnits
    );

    /**
     * 将预留金额退回可用余额
     * @param userId 用户编号
     * @param amountUnits 释放金额，必须为正整数
     * @return 更新行数，成功时为 1
     */
    int releaseBalance(
            @Param("userId") Long userId,
            @Param("amountUnits") Long amountUnits
    );

    /**
     * 按实际费用结算一次请求
     * 移除原预留金额，并退回差额或补扣费用
     * @param userId 用户编号
     * @param reserveUnits 原预留金额，必须为正数
     * @param actualCostUnits 实际费用，必须大于 0
     * @return 更新行数，成功时为 1，不满足条件时为 0
     */
    int settleBalance(
            @Param("userId") Long userId,
            @Param("reserveUnits") Long reserveUnits,
            @Param("actualCostUnits") Long actualCostUnits
    );
}
