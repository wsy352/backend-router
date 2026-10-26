package com.lingrui.router.client.impl;

import com.lingrui.router.client.UpstreamClient;
import com.lingrui.router.config.ChannelProperties;
import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.vo.ChatCompletionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * deepseek客户端，负责与deepseek平台进行http通信
 */
@Component
public class DeepseekClientImpl implements UpstreamClient{

    /**
     * 上游渠道配置，提供请求地址，API Key和模型名
     */
    @Autowired
    private ChannelProperties channelProperties;

    private final RestClient restClient = createRestClient();

    /** 创建带有超时设置的 HTTP 客户端 */
    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        //建立连接时的等待上限
        factory.setConnectTimeout(Duration.ofSeconds(10));

        //读取上游数据时的等待上限
        factory.setReadTimeout(Duration.ofSeconds(60));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * 调用deepseek的非流式聊天接口
     * @param request 聊天请求参数
     * @return 转换为统一格式的聊天响应
     */
    public ChatCompletionVO chat(ChatCompletionDTO request){
        //组合基础地址和聊天接口路径
        String url = channelProperties.getBaseUrl() + "/chat/completions";

        //组装上游请求体，使用配置中的真实模型名
        Map<String, Object> body = new HashMap<>();
        body.put("model", channelProperties.getModel());
        body.put("messages",request.getMessages());
        body.put("stream", false);

        //当前阶段关闭思考模式，并限制测试调用的输出长度
        Map<String, String> thinking = new HashMap<>();
        thinking.put("type", "disabled");
        body.put("thinking", thinking);
        body.put("max_tokens", request.getMaxTokens());

        //发送POST请求，并将相应JSON转换成聊天响应对象
        ChatCompletionVO response = restClient.post()// 创建一个POST请求
                .uri(url)//填写请求地址
                .contentType(MediaType.APPLICATION_JSON)//指定发送内容的格式是JSON
                .header("Authorization", "Bearer " + channelProperties.getApiKey())//填写调用deepseek所需的凭证
                .body(body)//填写要发送的内容
                .retrieve()//准备读取响应
                .body(ChatCompletionVO.class);//执行请求，读取返回的JSON，并转换成Java对象

        return response;
    }

    public void streamChat(ChatCompletionDTO request, Consumer<String> eventConsumer){
        String url = channelProperties.getBaseUrl() + "/chat/completions";

        //组装请求体
        Map<String ,Object> body = new HashMap<>();
        body.put("model", channelProperties.getModel());
        body.put("messages",request.getMessages());
        body.put("stream", true);

        //要求上游在结束前返回整次请求的用量
        Map<String, Object> streamOptions = new HashMap<>();
        streamOptions.put("include_usage", true);
        body.put("stream_options", streamOptions);

        //当前测试先关闭思考模式，与已有非流式调用保持一致
        Map<String, String> thinking = new HashMap<>();
        thinking.put("type", "disabled");
        body.put("thinking", thinking);

        //暂时沿用非流式测试的输出上限
        body.put("max_tokens", request.getMaxTokens());

        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", "Bearer " + channelProperties.getApiKey())
                .body(body)
                .exchange((httpRequest, httpResponse) -> {
                    //exchange需要我们自己去检查HTTP状态
                    if(!httpResponse.getStatusCode().is2xxSuccessful()){
                        throw new RestClientResponseException(
                                "上游流式请求失败",
                                httpResponse.getStatusCode().value(),
                                httpResponse.getStatusText(),
                                httpResponse.getHeaders(),
                                new byte[0],
                                StandardCharsets.UTF_8
                        );
                    }

                    //确认收到的是SSE,而不是普通JSON等其他内容
                    MediaType contentType = httpResponse.getHeaders().getContentType();

                    if(contentType == null || !MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)){
                        throw new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "上游未返回预期的 SSE 流"
                        );
                    }

                    //将响应的字节按照UTF-8解码，再逐行读取
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(httpResponse.getBody(),StandardCharsets.UTF_8))){

                        StringBuilder eventData = new StringBuilder();//创造一个能不断往后加文字的容器eventData
                        String line;

                        while((line = reader.readLine()) != null){
                            //空行表示当前事件结束
                            if(line.isEmpty()) {
                                if (eventData.length() == 0) {
                                    continue;
                                }

                                //去掉最后的换行符
                                String data = eventData.substring(0, eventData.length() - 1);//取子字符串，substring(从哪个下标开始, 到哪个下标为止)
                                eventData.setLength(0);

                                //把完整的事件数据交给调用方
                                eventConsumer.accept(data);

                                //正常结束：先转发标记，再结束读取
                                if ("[DONE]".equals(data)) {
                                    return null;
                                }
                            }else if(line.equals("data") || line.startsWith("data:")){
                                String value = line.equals("data") ? "" : line.substring(5);

                                //SSE允许冒号后有一个可选空格
                                if(value.startsWith(" ")){
                                    value = value.substring(1);
                                }

                                eventData.append(value).append('\n');
                            }

                            //当前只转发data内容，其他SSE自算和注释忽略
                        }
                    }

                    //连接关闭了，却没有收到正常结束标记
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "上游流在结束标记到达前中断"
                    );


                });

    }
}