package com.lingrui.router.service.impl;

import com.lingrui.router.dto.ChatMessageDTO;
import com.lingrui.router.service.RouterPrefixService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 请求前缀识别服务实现
 * 当前识别开头连续的 system 消息，保留原始内容和消息边界
 */
@Service
public class RouterPrefixServiceImpl implements RouterPrefixService {

    //前缀的最小字符长度，用于过滤过短的路由线索
    //这是本项目的筛选条件，不是上游缓存的 token 门槛
    @Value("${router.cache-routing.min-prefix-chars:256}")
    private int minPrefixChars;

    @Override
    public String calculatePrefixHash(List<ChatMessageDTO> messages) {

        if(minPrefixChars <= 0){
            throw new IllegalStateException("前缀最小字符长度必须大于零");
        }

        if(messages == null || messages.isEmpty()){
            return null;
        }

        MessageDigest messageDigest = createMessageDigest();

        //标识当前算法版本，未来调整规则时更换版本
        messageDigest.update(
                "system-prefix-v1".getBytes(StandardCharsets.UTF_8)
        );

        long prefixChars = 0;

        for(ChatMessageDTO chatMessageDTO : messages){
            if(chatMessageDTO == null){
                throw new IllegalArgumentException("消息不能为 null");
            }


            //只识别请求开头连续的 system 消息
            //遇到其他角色后停止,不能跳过中间消息继续拼接
            if(!"system".equals(chatMessageDTO.getRole())){
                break;
            }

            String content = chatMessageDTO.getContent();

            if(content == null || content.isBlank()){
                throw new IllegalArgumentException("system 消息内容不能为空");
            }

            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

            //先写入字节长度，确保多条消息之间的边界不会丢失
            messageDigest.update(ByteBuffer.allocate(Integer.BYTES).putInt(contentBytes.length).array());

            //原样计算摘要，不删除空格或换行
            messageDigest.update(contentBytes);

            prefixChars += content.codePointCount(0, content.length());
        }

        if(prefixChars < minPrefixChars){
            return null;
        }

        return HexFormat.of().formatHex(messageDigest.digest());
    }

    /**
     * 创建独立的摘要计算器
     * 每次调用单独创建，避免并发请求共享可变计算状态
     */
    private MessageDigest createMessageDigest() {

        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前运行环境不支持 SHA-256",
                    exception
            );
        }
    }
}
