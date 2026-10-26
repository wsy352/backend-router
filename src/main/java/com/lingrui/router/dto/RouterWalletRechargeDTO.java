package com.lingrui.router.dto;

import lombok.Data;

/**
 * 管理员增加测试余额的请求参数
 * 通过业务编号识别下重复提交，金额统一使用整数微元
 */
@Data
public class RouterWalletRechargeDTO {

    //充值业务编号：同一笔充值重试时保持不变，新充值使用新编号
    private String businessId;

    //充值金额，单位为微元
    private Long amountUnits;
}
