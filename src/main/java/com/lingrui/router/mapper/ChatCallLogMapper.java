package com.lingrui.router.mapper;

import com.lingrui.router.entity.ChatCallLog;
import com.lingrui.router.vo.ChatCallStatsVO;
import com.lingrui.router.vo.RouterCacheStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatCallLogMapper {

    /**
     * 保存一条调用记录，返回插入行数
     */
    int insert(ChatCallLog record);

    /**
     * 查询最近20条调用记录
     */
    List<ChatCallLog> selectRecent();

    /**
     * 统计全部调用记录的总数、成功数和失败数
     */
    ChatCallStatsVO selectStats();

    /**
     * 按请求编号查询并锁定调用记录
     * 必须在事务中调用，避免同一请求同时被结算或释放
     * @param requestId 中转站生成的请求编号
     * @return 调用记录，不存在时为 null
     */
    ChatCallLog selectByRequestIdForUpdate(@Param("requestId") String requestId);

    /**
     * 更新调用结果和资金状态，保留最初的身份，价格快照与预留预算
     * @param chatCallLog 本次调用的最终结果或待处理结果
     * @return 更新行数，正常应为 1
     */
    int updateResult(ChatCallLog chatCallLog);

    /**
     * 按编号分页查询资金尚未处理完成的请求
     * @param afterId 上一页最后一条记录的编号，第一页传 0
     * @return 最多 50 条未完成记录
     */
    List<ChatCallLog> selectUnfinishedBilling(
            @Param("afterId") Long afterId
    );

    /**
     * 按 key,渠道和模型别名汇总成功调用的缓存与费用信息
     * 缺失的缓存用量不作为零命中参与统计
     */
    List<RouterCacheStatsVO> selectCacheStats();
}
