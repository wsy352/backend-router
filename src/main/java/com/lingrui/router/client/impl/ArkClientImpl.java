package com.lingrui.router.client.impl;

import com.lingrui.router.client.ArkCacheClient;
import com.lingrui.router.client.UpstreamClient;
import com.lingrui.router.config.ArkProperties;
import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.dto.ChatMessageDTO;
import com.lingrui.router.vo.ArkPrefixCacheCreateVO;
import com.lingrui.router.vo.ArkResponseVO;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 火山方舟客户端，负责向方舟发送聊天请求
 */
@Component
public class ArkClientImpl implements UpstreamClient, ArkCacheClient {

    //读取router.ark下的配置
    @Autowired
    private ArkProperties arkProperties;

    //用于发送HTTP请求
    private final RestClient restClient = createRestClient();

    //创建带有超时设置的 HTTP 客户端
    private static RestClient createRestClient(){
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
     * 调用方舟 Responses API 创建前缀缓存
     */
    @Override
    public ArkPrefixCacheCreateVO createPrefixCache(
            List<ChatMessageDTO> prefixMessages,
            long expireAt
    ){
        if(prefixMessages == null || prefixMessages.isEmpty()){
            throw new IllegalArgumentException("缓存前缀消息不能为空");
        }

        long now = Instant.now().getEpochSecond();

        if(expireAt <= now || expireAt > now + 7L * 24 * 60 * 60){
            throw new IllegalArgumentException("缓存过期时间必须在未来七天内");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", arkProperties.getModel());
        body.put("input", prefixMessages);
        body.put("store", true);
        body.put("stream", false);
        body.put("expire_at", expireAt);

        //只创建前缀缓存，本次不生成聊天回答
        body.put("caching", Map.of(
                "type", "enabled",
                "prefix", true
        ));

        //后续复用缓存时也需要保持这个设置一致
        body.put("thinking", Map.of("type", "disabled"));

        ArkPrefixCacheCreateVO response = restClient.post()
                .uri(arkProperties.getBaseUrl() + "/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                        "Authorization",
                        "Bearer " + arkProperties.getApiKey()
                )
                .body(body)
                .retrieve()
                .body(ArkPrefixCacheCreateVO.class);

        if(response == null){
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "方舟创建缓存时返回了空响应"
            );
        }

        return response;
    }

    /**
     * 复用前缀缓存进行非流式生成
     * 此方法只负责上游通信，不负责鉴权，选路或扣款
     */
    @Override
    public ArkResponseVO chatWithPrefixCache(
            ChatCompletionDTO request,
            String upstreamModel,
            String upstreamResponseId,
            long expiresAt
    ) {
        if (request == null
                || request.getMessages() == null
                || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("聊天消息不能为空");
        }

        //流式需要单独处理 SSE，不能在这里退化成非流式
        if (Boolean.TRUE.equals(request.getStream())) {
            throw new IllegalArgumentException("此方法仅支持非流式调用");
        }

        if (request.getMaxTokens() == null
                || request.getMaxTokens() <= 0) {
            throw new IllegalArgumentException("最大输出 token 数必须大于零");
        }

        if (upstreamModel == null || upstreamModel.isBlank()
                || upstreamResponseId == null
                || upstreamResponseId.isBlank()) {
            throw new IllegalArgumentException("上游模型或缓存编号不能为空");
        }

        long now = Instant.now().getEpochSecond();

        if (expiresAt <= now
                || expiresAt > now + 7L * 24 * 60 * 60) {
            throw new IllegalArgumentException("缓存过期时间不合法");
        }

        Map<String, Object> body = new HashMap<>();

        //使用缓存记录中的模型，避免重新按别名选择其他模型
        body.put("model", upstreamModel);

        //只提交缓存前缀之后的消息，不重复发送已缓存的 system 内容
        body.put("input", request.getMessages());

        //始终引用原始前缀缓存，不自动串接其他调用者的对话
        body.put("previous_response_id", upstreamResponseId);

        body.put("stream", false);
        body.put("max_output_tokens", request.getMaxTokens());

        //与创建前缀缓存时的思考模式保持一致
        body.put("thinking", Map.of("type", "disabled"));

        //本轮正常生成回答，不设置 prefix=true
        body.put("caching", Map.of("type", "enabled"));

        //显式设置存储及到期时间，避免使用上游默认保存期限
        body.put("store", true);
        body.put("expire_at", expiresAt);

        ArkResponseVO response = restClient.post()
                .uri(arkProperties.getBaseUrl() + "/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                        "Authorization",
                        "Bearer " + arkProperties.getApiKey()
                )
                .body(body)
                .retrieve()
                .body(ArkResponseVO.class);

        if (response == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "方舟缓存聊天返回了空响应"
            );
        }

        //先完整交给上层，保留状态，错误信息和用量供后续处理
        return response;
    }



    /**
     * 调用方舟火山的非流式聊天接口
     * @param request 调用者传入的聊天请求
     * @return 转换后的聊天响应
     */
    @Override
    public ChatCompletionVO chat(ChatCompletionDTO request) {
        //拼出刚才在ApiFox中测试成功的地址
        String url = arkProperties.getBaseUrl() + "/chat/completions";

        //对应ApiFox中Body -> JSON的内容
        Map<String, Object> body = new HashMap<>();
        body.put("model", arkProperties.getModel());
        body.put("messages", request.getMessages());
        body.put("stream", false);
        body.put("max_tokens", request.getMaxTokens());

        //发送请求 ，再把响应 JSON 转换成 Java 对象
        ChatCompletionVO response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + arkProperties.getApiKey())
                .body(body)
                .retrieve()
                .body(ChatCompletionVO.class);

        return response;

    }


    /**
     * 调用火山方舟的流式聊天接口
     * @param request 聊天请求
     * @param eventConsumer 收到完整事件数据后执行的动作
     */
    @Override
    public void streamChat(ChatCompletionDTO request, Consumer<String> eventConsumer){
        String url = arkProperties.getBaseUrl() + "/chat/completions";

        //组装请求体
        Map<String ,Object> body = new HashMap<>();
        body.put("model", arkProperties.getModel());
        body.put("messages",request.getMessages());
        body.put("stream", true);
        body.put("max_tokens", request.getMaxTokens());

        //要求上游在结束前返回整次请求的用量
        Map<String, Object> streamOptions = new HashMap<>();
        streamOptions.put("include_usage", true);
        body.put("stream_options", streamOptions);

        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", "Bearer " + arkProperties.getApiKey())
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
                        }//循环结束
                    }

                    //连接关闭了，却没有收到正常结束标记
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "上游流在结束标记到达前中断"
                    );


        });

    }

}
