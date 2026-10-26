package com.lingrui.router.service.impl;

import com.lingrui.router.config.RouterAuthProperties;
import com.lingrui.router.context.RouterAuthContext;
import com.lingrui.router.entity.RouterApiKey;
import com.lingrui.router.entity.RouterUser;
import com.lingrui.router.mapper.RouterApiKeyMapper;
import com.lingrui.router.mapper.RouterUserMapper;
import com.lingrui.router.service.RouterAuthService;
import com.lingrui.router.util.ApiKeyHashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 验证调用者的中转站访问秘钥
 */
@Service
public class RouterAuthServiceImpl implements RouterAuthService {

    @Autowired
    private RouterAuthProperties routerAuthProperties;

    //秘钥数据访问接口：根据哈希查找秘钥记录及所属用户编号
    @Autowired
    private RouterApiKeyMapper routerApiKeyMapper;

    //用户数据访问接口：检查秘钥所属用户是否存在，是否启用
    @Autowired
    private RouterUserMapper routerUserMapper;

    /**
     * 验证聊天秘钥
     * 秘钥和所属用户都必须存在且处于启用状态
     */
    @Override
    public RouterAuthContext validateChatKey(String authorization) {
        //检查请求头格式
        if(authorization == null
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请提供有效的访问秘钥"
            );
        }

        //取出完整秘钥，并检查是否为空
        String apiKey = authorization.substring(7);

        if(apiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请提供有效的聊天访问秘钥"
            );
        }

        //数据库保存的是哈希，因此先计算哈希，再查询记录
        String keyHash = ApiKeyHashUtil.caculateKeyHash(apiKey);

        RouterApiKey routerApiKey = routerApiKeyMapper.selectByKeyHash(keyHash);

        //不存在或已停用的秘钥不能调用模型
        if(routerApiKey == null || !"ENABLED".equals(routerApiKey.getStatus())){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "聊天访问秘钥无效或已停用"
            );
        }

        //使用逻辑外键，需要由程序员检查所属用户
        RouterUser routerUser = routerUserMapper.selectById(routerApiKey.getUserId());

        if(routerUser == null || !"ENABLED".equals(routerUser.getStatus())){
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "秘钥所属用户不存在或已停用"
            );
        }

        RouterAuthContext routerAuthContext = new RouterAuthContext();
        routerAuthContext.setUserId(routerUser.getId());
        routerAuthContext.setApiKeyId(routerApiKey.getId());

        return routerAuthContext;
    }

    /**
     * 验证管理员秘钥
     */
    @Override
    public void validateAdminKey(String authorization) {
        validateBearerKey(authorization, routerAuthProperties.getAdminKey());
    }

    /**
     * 校验 Bearer 格式，并与配置中的管理员秘钥比对
     */
    private void validateBearerKey(String authorization, String expectedKey){

        if(expectedKey == null || expectedKey.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "服务端尚未配置访问秘钥"
            );
        }

        if(authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请提供有效的访问秘钥"
            );
        }

        String provideKey = authorization.substring(7);

        if(!expectedKey.equals(provideKey)){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请提供有效的访问秘钥"
            );
        }
    }
}
