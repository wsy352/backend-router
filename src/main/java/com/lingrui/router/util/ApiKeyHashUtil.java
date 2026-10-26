package com.lingrui.router.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * API 秘钥哈希工具
 * 创建秘钥和验证秘钥时，使用相同的算法计算哈希
 */
public class ApiKeyHashUtil {

    //工具类只提供静态方法，不需要创建对象
    private ApiKeyHashUtil(){
    }

    /**
     * 将完整秘钥转换为64位十六进制 SHA-256 哈希字符串
     */
    public static String caculateKeyHash(String apiKey){
        try{
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = messageDigest.digest(apiKey.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException noSuchAlgorithmException){
            throw new IllegalStateException(
                    "当前运行环境不支持 SHA-256",
                    noSuchAlgorithmException
            );
        }
    }
}
