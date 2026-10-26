package com.lingrui.router.dto;

import lombok.Data;

/**
 * 管理员释放预留资金的请求
 * 不接收退款金额，金额以原预留流水为准
 */
@Data
public class ChatBillingReleaseDTO {

    //请求所属用户编号，用于核对请求归属
    private Long userId;

    //管理员完成核对后填写的释放原因
    private String reason;
}
