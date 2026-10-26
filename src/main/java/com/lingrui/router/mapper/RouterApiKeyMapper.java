package com.lingrui.router.mapper;

import com.lingrui.router.entity.RouterApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * API 秘钥数据访问接口
 */
@Mapper
public interface RouterApiKeyMapper {

    /**
     * 保存秘钥记录，返回插入行数
     */
    int insert(RouterApiKey routerApiKey);

    /**
     * 根据秘钥哈希查询记录
     */
    RouterApiKey selectByKeyHash(@Param("keyHash") String keyHash);

    /**
     * 根据用户编号和秘钥编号查询记录，确认秘钥属于该用户
     */
    RouterApiKey selectByUserIdAndId(@Param("userId") Long userId, @Param("id") Long id);

    /**
     * 停用指定用户的秘钥，保留记录供后续查询和对账
     */
    int disableByUserIdAndId(@Param("userId") Long userId, @Param("id") Long id);
}
