package com.lingrui.router.mapper;

import com.lingrui.router.entity.RouterWalletTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 钱包资金流水数据访问接口
 * 负责保存流水，并根据业务编号查询已有流水
 */
@Mapper
public interface RouterWalletTransactionMapper {

    /**
     * 保存一笔资金流水
     * @param routerWalletTransaction 资金变化及操作后的余额
     * @return 插入行数，成功时为 1
     */
    int insert(RouterWalletTransaction routerWalletTransaction);

    /**
     * 查询指定业务对应的资金流水
     * 用于判断同一次充值或结果是否已经处理
     * @param userId 用户编号
     * @param businessId 业务编号
     * @param transactionType 流水类型
     * @return 已有流水，不存在时返回 null
     */
    RouterWalletTransaction selectByBusiness(
            @Param("userId") Long userId,
            @Param("businessId") String businessId,
            @Param("transactionType") String transactionType
    );
}
