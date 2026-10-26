package com.lingrui.router.ArkResponseConverter;

import com.lingrui.router.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将方舟 Responses 响应转换为项目统一聊天响应
 * 不负责上游请求和资金操作
 */
@Component
public class ArkResponseConverter {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 转换用量字段，未知用量仍保留 null
     */
    public UsageVO toUsage(ArkResponseUsageVO source){

        if (source == null) {
            return null;
        }

        UsageVO usage = new UsageVO();

        usage.setPromptTokens(source.getInputTokens());
        usage.setCompletionTokens(source.getOutputTokens());
        usage.setTotalTokens(source.getTotalTokens());
        usage.setPromptTokensDetails(source.getInputTokensDetails());

        return usage;

    }

    /**
     * 将方舟非流式文本响应转换为统一聊天响应
     */
    public ChatCompletionVO toChatCompletion(ArkResponseVO source){
        if(source == null){
            throw badResponse("方舟返回了空响应");
        }

        //不向调用方直接透传上游错误详情
        if(source.getError() != null
                && !source.getError().isNull()){
            throw badResponse("方舟生成失败");
        }

        String finishReason;

        if("completed".equals(source.getStatus())){

            //正常完成
            finishReason = "stop";

        } else if("incomplete".equals(source.getStatus())
            && source.getIncompleteDetails() != null
            && "max_output_tokens".equals(
                source.getIncompleteDetails()
                        .path("reason")
                        .asText())){

            //达到输出长度限制，保留已经生成的回答和用量
            finishReason = "length";
        } else{
            throw badResponse("方舟未返回可转换的生成结果");
        }

        //检查统一聊天响应需要的基础字段
        if(source.getId() == null
                || source.getId().isBlank()
                || source.getModel() == null
                || source.getModel().isBlank()
                || source.getCreatedAt() == null){
            throw badResponse("方舟响应缺少必要字段");
        }

        StringBuilder text = new StringBuilder();
        boolean foundText = false;

        //output 是数组，可能包含消息、推理内容等不同项目
        JsonNode output = source.getOutput();

        if(output != null && output.isArray()) {
            for (JsonNode item : output) {

                //只读取助手消息，忽略推理项目等其他类型
                if (!"message".equals(item.path("type").asText())
                        || !"assistant".equals(item.path("role").asText())) {
                    continue;
                }

                //一条助手消息内部可能包含多个内容片段
                JsonNode content = item.path("content");

                if (!content.isArray()) {
                    continue;
                }

                for (JsonNode part : content) {

                    //只拼接明确标记为 output_text 的文本片段

                    if ("output_text".equals(part.path("type").asText())
                            && part.path("text").isTextual()) {
                        text.append(part.path("text").asText());
                        foundText = true;
                    }
                }
            }
        }

        //长度限制可能导致尚未生成正文
        //正常完成却没有文本时，不伪造一个成功的空回答
        if(!foundText && !"length".equals(finishReason)){
            throw badResponse("方舟响应中没有文本回答");
        }

        //组装助手消息
        ChatMessageVO message = new ChatMessageVO();

        message.setRole("assistant");
        message.setContent(text.toString());

        //组装回答选项，当前只返回一个文本回答
        ChatChoiceVO choice = new ChatChoiceVO();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason(finishReason);

        //组装统一聊天响应
        ChatCompletionVO response = new ChatCompletionVO();
        response.setId(source.getId());
        response.setObject("chat.completion");
        response.setCreated(source.getCreatedAt());
        response.setModel(source.getModel());
        response.setChoices(List.of(choice));
        response.setUsage(toUsage(source.getUsage()));

        return response;
    }

    /**
     * 统一构造上游响应异常
     * HTTP 502 表示中转站没有取得可用的上游响应
     */
    private ResponseStatusException badResponse(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                message
        );
    }
}
