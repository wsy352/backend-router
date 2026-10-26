package com.lingrui.router.controller;

import com.lingrui.router.dto.RouterUserCreateDTO;
import com.lingrui.router.entity.RouterUser;
import com.lingrui.router.service.RouterAuthService;
import com.lingrui.router.service.RouterService;
import com.lingrui.router.service.RouterUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员用户管理接口
 */
@RestController
@RequestMapping("/api/admin/users")
public class RouterUserController {

    @Autowired
    private RouterUserService routerUserService;

    @Autowired
    private RouterAuthService routerAuthService;

    /**
     * 创建用户
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RouterUser CreateUser(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestBody RouterUserCreateDTO routerUserCreateDTO
    ) {
        routerAuthService.validateAdminKey(authorization);

        return routerUserService.createUser(routerUserCreateDTO.getUsername());
    }

    /**
     * 根据编号查询用户
     */
    @GetMapping("/{id}")
    public RouterUser getById(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @PathVariable("id") Long id
    ){
        routerAuthService.validateAdminKey(authorization);
        return routerUserService.getById(id);
    }
}
