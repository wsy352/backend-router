package com.lingrui.router.service.impl;

import com.lingrui.router.mapper.RouterPrefixHistoryMapper;
import com.lingrui.router.service.RouterPrefixHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 前缀历史服务实现
 * 只推荐当前配置允许使用的渠道
 */
@Service
public class RouterPrefixHistoryServiceImpl implements RouterPrefixHistoryService {

    //前缀历史数据访问接口，读取历史渠道并保存成功记录
    @Autowired
    private RouterPrefixHistoryMapper routerPrefixHistoryMapper;

    //本地历史线索有效期，默认 30 分钟，不代表上游缓存时长
    @Value("${router.cache-routing.history-ttl-seconds:1800}")
    private long historyTtlSeconds;

    @Override
    public String findChannel(
            Long apiKeyId,
            String modelAlias,
            String prefixHash,
            List<String> candidateChannels) {

        if(prefixHash == null){
            return null;
        }

        List<String> historyChannels = routerPrefixHistoryMapper.selectRecentChannels(
                apiKeyId, modelAlias, prefixHash);

        //查询结果已经按最近成功时间排序
        for(String channel : historyChannels){
            if(candidateChannels.contains(channel)){
                return channel;
            }
        }
        return null;
    }

    @Override
    public void saveSuccess(
            Long apiKeyId,
            String modelAlias,
            String prefixHash,
            String channel
    ){

        if(prefixHash == null){

            return ;
        }

        if(historyTtlSeconds <= 0 || historyTtlSeconds > 604800){
            throw new IllegalStateException("前缀历史有效期必须在 1 至 604800 秒之间");
        }

        routerPrefixHistoryMapper.saveSuccess(
                apiKeyId,
                modelAlias,
                prefixHash,
                channel,
                historyTtlSeconds);
    }

}
