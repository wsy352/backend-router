package com.lingrui.router.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 中转站用户
 */
@Data
public class RouterUser {

    //用户编号，数据库自增主键，插入后由 Mapper 回填
    private Long id;

    //用户名，全表唯一，最长 64 个字符
    private String username;

    // 状态：ENABLED 正常，其他值视为停用
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
