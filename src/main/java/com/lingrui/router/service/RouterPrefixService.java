package com.lingrui.router.service;

import com.lingrui.router.dto.ChatMessageDTO;

import java.util.List;

/**
 * 请求前缀识别服务
 * 为开头连续的 system 消息生成稳定标识，供缓存感知路由查询历史
 */
public interface RouterPrefixService {

    /**
     * 计算稳定前缀摘要
     * 没有符合条件的前缀时返回 null
     * 调用前应先完成聊天消息参数校验
     */
    String calculatePrefixHash(List<ChatMessageDTO> messages);
}
