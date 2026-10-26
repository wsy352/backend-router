package com.lingrui.router.mapper;

import com.lingrui.router.entity.RouterUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


/**
 * 用户访问接口
 */
@Mapper
public interface RouterUserMapper {

    /**
     * 保存用户，返回插入函数
     */
    int insert(RouterUser routerUser);

    /**
     * 根据用户编号查询用户
     */
    RouterUser selectById(@Param("id") Long id);
}
