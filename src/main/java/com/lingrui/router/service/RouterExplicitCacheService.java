package com.lingrui.router.service;

import com.lingrui.router.context.RouterAuthContext;
import com.lingrui.router.dto.ExplicitCacheCreateDTO;
import com.lingrui.router.entity.RouterExplicitCache;
import com.lingrui.router.vo.ExplicitCacheVO;

/**
 * 显式缓存业务接口
 * 负责参数检查，归属校验，上游调用和创建费用处理
 */
public interface RouterExplicitCacheService {

    /**
     * 为当前鉴权通过的 key 创建显示缓存
     * @param request 调用方提交的缓存创建参数
     * @param authContext 服务端鉴权后得到的用户和 key 信息
     * @return 中转站缓存信息
     */
    ExplicitCacheVO create(
            ExplicitCacheCreateDTO request,
            RouterAuthContext authContext
    );

    /**
     * 查询当前 key 创建的缓存
     * 不属于当前 key 的缓存不允许访问
     * @param cacheId 中转站缓存编号
     * @param authContext 服务端鉴权结果
     * @return 中转站缓存信息
     */
    ExplicitCacheVO getOwned(
            String cacheId,
            RouterAuthContext authContext
    );

    /**
     * 查询并检查可用于聊天的缓存
     * 仅供内部业务调用，不直接作为接口响应返回
     * @param cacheId 中转站缓存编号
     * @param modelAlias 本次聊天使用的模型别名
     * @param authContext 服务端鉴权结果
     * @return 通过归属，状态，有效期和模型检查的缓存记录
     */
    RouterExplicitCache requireUsable(
            String cacheId,
            String modelAlias,
            RouterAuthContext authContext
    );
}
