package com.lingrui.router.mapper;

import com.lingrui.router.entity.RouterExplicitCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 显式缓存数据访问接口
 */
@Mapper
public interface RouterExplicitCacheMapper {

    /**
     * 调用上游前，先保存 CREATING 状态的记录
     * 返回新增行数，自增主键回填到实体的 id
     */
    int insertCreating(RouterExplicitCache cache);

    /**
     * 根据缓存编号和所属 key 查询记录
     * 不存在或不属于当前 key 时返回 null
     * 状态和过期时间交给业务层检查
     */
    RouterExplicitCache selectOwned(
            @Param("cacheId") String cacheId,
            @Param("apiKeyId") Long apiKeyId
    );

    /**
     * 上游明确创建成功后，保存响应编号和实际过期时间
     * 仅允许从 CREATING 更新为 READY
     */
    int markReady(
            @Param("cacheId") String cacheId,
            @Param("apiKeyId") Long apiKeyId,
            @Param("upstreamResponseId") String upstreamResponseId,
            @Param("expiresAt") Long expiresAt
    );

    /**
     * 上游明确创建失败
     * 仅允许从 CREATING 更新为 FAILED
     */
    int markFailed(
            @Param("cacheId") String cacheId,
            @Param("apiKeyId") Long apiKeyId
    );

    /**
     * 超时或连接中断，无法确认上游是否创建成功
     * 仅允许从 CREATING 更新为 UNKNOWN
     */
    int markUnknown(
            @Param("cacheId") String cacheId,
            @Param("apiKeyId") Long apiKeyId
    );
}
