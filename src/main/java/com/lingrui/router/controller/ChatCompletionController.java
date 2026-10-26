package com.lingrui.router.controller;

import com.lingrui.router.context.RouterAuthContext;
import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.service.RouterAuthService;
import com.lingrui.router.service.RouterService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 *聊天接口，负责接收调用者的聊天请求
 */
@Slf4j
@RestController
@RequestMapping("/v1")
public class ChatCompletionController {

    @Autowired
    private RouterService routerService;

    @Autowired
    private RouterAuthService routerAuthService;

    /**
     * 根据 stream 参数，处理非流式或流式聊天请求
     * @param request 聊天请求
     * @param response 发送给调用者的 HTTP 响应
     * @param sessionId 可选的会话编号，用于优先复用历史渠道
     * @return 非流式返回响应对象；流式已直接写入响应，返回null
     */
    @PostMapping("/chat/completions")
    public Object chat(@RequestBody ChatCompletionDTO request,
                       @RequestHeader(value =  "Authorization", required = false)
                       String authorization,
                       @RequestHeader(value = "X-Session-Id", required = false)
                               String sessionId,
                       HttpServletResponse response) throws IOException {

        //验证秘钥，并接收服务端查询得到的用户身份
        RouterAuthContext routerAuthContext = routerAuthService.validateChatKey(authorization);

        //非流式：仍然按照原来的方式返回完整响应
        if(!Boolean.TRUE.equals(request.getStream())){
            return routerService.chat(request, routerAuthContext, sessionId);
        }

        try{
            //流式：收到一条事件，就发送一条事件
            routerService.streamChat(
                    request,
                    routerAuthContext,
                    sessionId,
                    data -> sendSseEvent(response, data)
            );
        } catch(UncheckedIOException exception){
            //向调用者发送数据失败，停止处理，不再继续发送
            throw exception.getCause();
        } catch(RuntimeException exception){
            //尚未发送响应时，仍可交给全局异常处理器
            if(!response.isCommitted()){
                response.reset();
                throw exception;
            }

            //已经开始发送后发生异常，记录原因，便于排查
            log.error("流式响应发送过程在发生异常", exception);

            //已经开始发送后，不能再改变 HTTP 状态码
            //用流内错误事件告诉调用者，不伪造正常结束标记
            sendSseEvent(
                    response,
                    "{\"error\":{\"code\":\"stream_error\","
                             + "\"message\":\"流式响应中断，请重新发起请求\"}}"
            );

        }

        //数据已通过过 response 直接发送，不再返回一份 JSON
        return null;
    }

    /**
     * 将一条事件数据编码为 SSE 格式并发送
     * @param response 发送给调用者的 HTTP 响应
     * @param data 事件数据，不包含 data: 前缀
     */
    private void sendSseEvent(HttpServletResponse response, String data){

        try {
            //第一次发送事件时，设置响应格式
            if(!response.isCommitted()){
                response.setContentType("text/event-stream");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-cache");
            }

            //一条事件的数据可能有多行，每一行都加 data: 前缀
            StringBuilder event = new StringBuilder();

            for(String line : data.split("\n", -1)){
                event.append("data: ").append(line).append('\n');
            }

            //再加一个换行，形成事件之间的空行
            event.append('\n');

            //将文字编码成字节，写入响应
            response.getOutputStream().write(
                    event.toString().getBytes(StandardCharsets.UTF_8)
            );

            //立即将缓冲中的数据发出
            response.flushBuffer();
        } catch(IOException exception) {
            //Consumer.accept 不允许直接抛出受检的 IOException
            //所以先包装成运行时异常，交给外层处理
            throw new UncheckedIOException(exception);
        }
    }


}
