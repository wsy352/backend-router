package com.lingrui.router.client;

import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.vo.ChatCompletionVO;

import java.util.function.Consumer;

/**
 * 上游模型客户端，定义向平台发起请求的能力
 */
public interface UpstreamClient {
    /**
     * 向上游平台发起非流式聊天请求
     * @param request 聊天请求
     * @return 转换为统一格式的聊天响应
     */
    ChatCompletionVO chat(ChatCompletionDTO request);

    /**
     * 向上游平台发起流式聊天请求
     * @param request 聊天请求
     * @param eventConsumer 收到一条完整事件的数据后执行的动作
     */
    default void streamChat(ChatCompletionDTO request, Consumer<String> eventConsumer){
        //临时默认实现，后续由各平台客户端实现真正的流式调用
        throw new UnsupportedOperationException("该客户端尚未实现流式调用");
    }





}
