package com.lingrui.router.mapper;

import com.lingrui.router.entity.RouterSessionBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会话绑定数据访问接口
 * 负责查询有效绑定，以及创建或刷新绑定
 */
@Mapper
public interface RouterSessionBindingMapper {

    /**
     * 查询尚未过期的会话绑定
     * 不存在或已经过期时返回 null
     */
    RouterSessionBinding selectActive(
            @Param("apiKeyId") Long apiKeyId,
            @Param("modelAlias") String modelAlias,
            @Param("sessionId") String sessionId
    );

    /**
     * 保存会话绑定
     * 唯一键已存在时，更新渠道并刷新有效期
     */
    int saveBinding(
            @Param("apiKeyId") Long apiKeyId,
            @Param("modelAlias") String modelAlias,
            @Param("sessionId") String sessionId,
            @Param("channel") String channel,
            @Param("ttlSeconds") long ttlSeconds
    );
}
