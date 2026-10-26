package com.lingrui.router.service;

import com.lingrui.router.model.NormalizedUsage;
import com.lingrui.router.vo.UsageVO;

/**
 * 用量归一化服务
 * 将不同平台的用量字段转换成中转站统一的数据结构
 */
public interface UsageNormalizationService {

    /**
     * 转换并检查上游用量
     * @param channel 渠道标识，当前支持 deepseek ,ark
     * @param usageVO 上游用量，可以为 null
     * @return 中转站统一的用量结构，如果上游用量为 null，则返回 null
     */
    NormalizedUsage normalize(String channel, UsageVO usageVO);
}
