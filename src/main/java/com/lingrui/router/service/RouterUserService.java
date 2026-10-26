package com.lingrui.router.service;

import com.lingrui.router.entity.RouterUser;

/**
 * 用户管理服务
 */
public interface RouterUserService {

    /**
     * 创建用户
     */
    RouterUser createUser(String username);

    /**
     * 根据编号查询用户
     */
    RouterUser getById(Long id);
}
