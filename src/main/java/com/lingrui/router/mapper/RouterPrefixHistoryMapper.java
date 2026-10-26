package com.lingrui.router.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 请求前缀历史数据访问接口
 * 查询处理过相同前缀的渠道，并记录最近成功调用
 */
@Mapper
public interface RouterPrefixHistoryMapper {

    /**
     * 查询有效的历史渠道，最近成功调用的渠道排在前面
     * 没有记录时返回空列表
     */
    List<String> selectRecentChannels(
            @Param("apiKeyId") Long apiKeyId,
            @Param("modelAlias") String modelAlias,
            @Param("prefixHash") String prefixHash
    );

    /**
     * 记录成功调用，并刷新该渠道的前缀线索有效期
     * 同一条记录已存在时更新，不重复创建
     */
    int saveSuccess(
            @Param("apiKeyId") Long apiKeyId,
            @Param("modelAlias") String modelAlias,
            @Param("prefixHash") String prefixHash,
            @Param("channel") String channel,
            @Param("ttlSeconds") long ttlSeconds
    );
}
