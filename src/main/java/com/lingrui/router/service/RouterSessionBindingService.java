package com.lingrui.router.service;

/**
 * 会话绑定服务
 * 为路由提供历史渠道偏好，并保存成功调用的渠道
 */
public interface RouterSessionBindingService {

    /**
     * 查询会话优先使用的渠道
     * 未提供会话编号，或者没有有效绑定时返回 null
     */
    String findChannel(
            Long apiKeyId,
            String modelAlias,
            String sessionId
    );

    /**
     * 保存成功调用使用的渠道，并刷新绑定有效期
     * 调用方需要先确认渠道属于当前模型的候选渠道
     */
    void saveBinding(
            Long apiKeyId,
            String modelAlias,
            String sessionId,
            String channel
    );
}
