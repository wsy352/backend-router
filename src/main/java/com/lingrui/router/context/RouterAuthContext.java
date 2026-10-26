package com.lingrui.router.context;

import lombok.Data;

/**
 * 当前请求的身份信息
 * 由服务端根据秘钥查询得到，传给聊天服务用于记录日志和后续收费
 */
@Data
public class RouterAuthContext {

    private Long userId;  //发起本次请求的用户编号

    private Long apiKeyId;  //本次请求使用的秘钥编号
}
