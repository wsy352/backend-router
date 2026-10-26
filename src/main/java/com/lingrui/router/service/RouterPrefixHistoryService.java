package com.lingrui.router.service;

import java.util.List;

/**
 * 前缀历史任务
 * 根据有效历史推荐候选渠道，并记录成功调用
 * 参数由完成鉴权和参数校验的路由服务传入
 */
public interface RouterPrefixHistoryService {

    /**
     * 从历史中查找仍属于当前候选列表的渠道
     * 没有匹配线索时返回 null
     */
    String findChannel(
            Long apiKeyId,
            String modelAlias,
            String prefixHash,
            List<String> candidateChannels
    );

    /**
     * 保存成功调用的前缀历史
     * 没有稳定前缀时不保存
     */
    void saveSuccess(
            Long apiKeyId,
            String modelAlias,
            String prefixHash,
            String channel
    );

}
