package com.lingrui.router.client;

import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.dto.ChatMessageDTO;
import com.lingrui.router.vo.ArkPrefixCacheCreateVO;
import com.lingrui.router.vo.ArkResponseVO;
import com.lingrui.router.vo.ChatMessageVO;

import java.util.List;

/**
 * 方舟显式缓存接口
 */
public interface ArkCacheClient {

    /**
     * 创建可复用的前缀缓存
     * @param prefixMessages 固定的前缀消息
     * @param expireAt 过期时间，Unix 时间戳，单位为秒
     * @return 上游缓存创建结果
     */
    ArkPrefixCacheCreateVO createPrefixCache(
            List<ChatMessageDTO> prefixMessages,
            long expireAt
    );

    /**
     * 使用已经校验过的前缀缓存，进行非流式聊天
     * @param request 本次聊天参数，messages 为缓存前缀之后的消息
     * @param upstreamModel 缓存创建时使用的上游模型
     * @param upstreamResponseId 缓存对应的上游编号
     * @param expiresAt 本轮响应的过期时刻，Unix 秒
     * @return 方舟原始响应对象，后续转换成统一聊天格式
     */
    ArkResponseVO chatWithPrefixCache(
            ChatCompletionDTO request,
            String upstreamModel,
            String upstreamResponseId,
            long expiresAt
    );
}
