package com.lingrui.router.entity;

import lombok.Data;

/**
 * 一次模型调用的记录，对应 chat_call_log 表中的一行
 */
@Data
public class ChatCallLog {

    //记录编号，数据库自增主键，仅用于定位这一行
    private Long id;

    //中转站为本次调用生成的请求编号，全表唯一
    private String requestId;

    //发起调用的用户编号，用于后续按用户统计和计费
    private Long userId;

    //本次使用的秘钥编号，用于按 Key 查询用量和费用
    private Long apiKeyId;

    //调用方请求里使用的模型别名
    private String modelAlias;

    //实际调用的上游渠道标识
    private String channel;

    //是否为流式调用
    private Boolean stream;

    //输入 token 总数，包含缓存命中部分；null 表示上游未提供
    private Long promptTokens;

    //输入总量中命中缓存的 token 数，null 表示未知，0 表示明确没有命中
    private Long cachedInputTokens;

    //输出 token 数，null 表示上游未提供
    private Long completionTokens;

    //输入与输出的总 token 数，null 表示上游未提供
    private Long totalTokens;

    //调用状态：PROCESSING 处理中，SUCCESS 成功，FAILED 失败
    private String status;

    //失败时的错误分类，成功时为 null
    private String errorCode;

    //调用耗时，单位毫秒
    private Long durationMs;

    //创建时间，即调用开始时间
    private java.time.LocalDateTime createdAt;

    //本次调用使用的价格版本，便于追溯价格调整
    private String priceVersion;

    //普通输入单价快照，单位：微元 / 百万 token
    private Long inputUnitsPerMillion;

    //缓存命中输入单价快照，单位：微元 / 百万 token
    private Long cachedInputUnitsPerMillion;

    //普通输出单价快照，单位：微元 / 百万 token
    private Long outputUnitsPerMillion;

    //根据实际用量和价格快照计算的费用，单位微元；不代表已经扣款
    private Long costUnits;

    //费用计算状态：CALCULATED, PENDING_USAGE, CALCULATION_FAILED, NOT_CALCULATED
    private String costCalculationStatus;

    //本次请求计划预留的金额，单位微元；实际资金变动以钱包流水为准
    private Long reservedUnits;

    //资金状态：RESERVED 已预留，SETTLED 已结算，RELEASED 已释放，PENDING 待处理
    private String billingStatus;

    //最后更新时间，用于定位长时间没有完成处理的请求
    private java.time.LocalDateTime updatedAt;

    //管理员核对后的资金释放原因，未释放时为空
    private String releaseReason;

}
