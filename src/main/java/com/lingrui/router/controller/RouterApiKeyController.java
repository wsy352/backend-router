package com.lingrui.router.controller;

import com.lingrui.router.dto.RouterApiKeyCreateDTO;
import com.lingrui.router.service.RouterApiKeyService;
import com.lingrui.router.service.RouterAuthService;
import com.lingrui.router.vo.RouterApiKeyCreateVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * API 秘钥管理接口
 * 管理员可以为指定用户创建中转站 API 秘钥
 */
@RestController
@RequestMapping("/api/admin/users/{userId}/api-keys")
public class RouterApiKeyController {

    //秘钥管理服务，负责生成秘钥，计算哈希值并保存到数据库
    @Autowired
    private RouterApiKeyService routerApiKeyService;

    //身份验证服务：检查调用者是否提供了正确的管理员秘钥
    @Autowired
    private RouterAuthService routerAuthService;

    /**
     * 为指定用户创建 API 秘钥
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) //方法正常完成时返回 HTTP 201
    public RouterApiKeyCreateVO createApiKey(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @PathVariable("userId") Long userId,
            @RequestBody RouterApiKeyCreateDTO routerApiKeyCreateDTO
            ){

        //先验证管理员秘钥
        routerAuthService.validateAdminKey(authorization);

        //取出秘钥名称，创建秘钥，返回秘钥信息
        return routerApiKeyService.createApiKey(
                userId,
                routerApiKeyCreateDTO.getKeyName()
        );
    }

    /**
     * 管理员吊销指定用户的密码
     * 成功后返回204，不返回响应体
     */
    @PostMapping("/{apiKeyId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeApiKey(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @PathVariable("userId") Long userId,
            @PathVariable("apiKeyId") Long apiKeyId
    ){
        //吊销属于敏感操作，先验证管理员秘钥
        routerAuthService.validateAdminKey(authorization);
        //吊销指定用户指定的秘钥
        routerApiKeyService.revokeApiKey(userId,apiKeyId);
    }
}
