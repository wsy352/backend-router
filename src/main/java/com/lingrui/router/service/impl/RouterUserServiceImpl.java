package com.lingrui.router.service.impl;

import com.lingrui.router.entity.RouterUser;
import com.lingrui.router.mapper.RouterUserMapper;
import com.lingrui.router.service.RouterUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 用户管理服务实现
 */
@Service
public class RouterUserServiceImpl implements RouterUserService {

    @Autowired
    private RouterUserMapper routerUserMapper;

    @Override
    public RouterUser createUser(String username){
        //校验用户名
        if(username == null || username.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户名不能为空"
            );
        }

        String normalizedUsername = username.strip(); //去掉字符串首尾的空白

        if(normalizedUsername.length() > 64){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户名长度不能超过64个字符"
            );
        }

        //准备用户数据
        LocalDateTime now = LocalDateTime.now();

        RouterUser routerUser = new RouterUser();
        routerUser.setUsername(normalizedUsername);
        routerUser.setStatus("ENABLED");
        routerUser.setCreatedAt(now);
        routerUser.setUpdatedAt(now);

        //保存到数据库
        try{
            routerUserMapper.insert(routerUser);
        } catch(DuplicateKeyException duplicateKeyException){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "用户名已存在"
            );
        }

        //Mapper 会把数据库生成的id 填回这个对象
        return routerUser;
    }

    @Override
    public RouterUser getById(Long id){
        if(id == null || id <= 0){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户编号必须为正整数"
            );
        }

        RouterUser routerUser = routerUserMapper.selectById(id);

        if(routerUser == null){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "用户不存在"
            );
        }

        return routerUser;
    }
}
