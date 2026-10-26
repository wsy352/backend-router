package com.lingrui.router.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 非流式聊天响应中的一个回答选项
 */
@Data
public class ChatChoiceVO {
    private Integer index;//回答选项的顺序，从0开始
    private ChatMessageVO message;//模型回复的消息，包含角色和内容
    @JsonProperty("finish_reason")
    private String finishReason; // 回答结束的原因，stop正常结束、length生成长度限制
}
