package com.lingrui.router.controller;

import com.lingrui.router.context.RouterAuthContext;
import com.lingrui.router.dto.ExplicitCacheCreateDTO;
import com.lingrui.router.service.RouterAuthService;
import com.lingrui.router.service.RouterExplicitCacheService;
import com.lingrui.router.vo.ExplicitCacheVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 显式缓存接口
 * 使用中转站聊天 key 鉴权
 */
@RestController
@RequestMapping("/api/explicit-caches")
public class RouterExplicitCacheController {

    //验证中转站 key, 取得服务端确认的身份信息
    @Autowired
    private RouterAuthService routerAuthService;

    //处理缓存创建和归属查询
    @Autowired
    private RouterExplicitCacheService routerExplicitCacheService;

    /**
     * 创建属于当前 key 的缓存
     * 创建成功返回 HTTP 201
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExplicitCacheVO create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ExplicitCacheCreateDTO request
            ){
        //鉴权失败时直接退出，不执行缓存创建
        RouterAuthContext authContext = routerAuthService.validateChatKey(authorization);

        return routerExplicitCacheService.create(request, authContext);

    }

    /**
     * 查询当前 key 拥有的缓存
     * 不存在或属于其他 key 时，由业务层统一返回 404
     */
    @GetMapping("/{cacheId}")
    public ExplicitCacheVO getOwned(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("cacheId") String cacheId
    ){
        RouterAuthContext authContext = routerAuthService.validateChatKey(authorization);

        return routerExplicitCacheService.getOwned(cacheId, authContext);
    }
}
