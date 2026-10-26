package com.lingrui.router.task;

import com.lingrui.router.config.RecoveryProperties;
import com.lingrui.router.entity.ChatCallLog;
import com.lingrui.router.mapper.ChatCallLogMapper;
import com.lingrui.router.service.ChatBillingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 滞留资金巡检任务
 * 只做 发现，警告，有限重试 ，不自动退款
 */
@Slf4j
@Component
public class BillingRecoveryTask {

    //读取巡检配置
    @Autowired
    private RecoveryProperties recoveryProperties;

    //查询滞留记录
    @Autowired
    private ChatCallLogMapper chatCallLogMapper;

    //重试结算，方法内部会判断是否已经结算过
    @Autowired
    private ChatBillingService chatBillingService;

    /**
     * 定期扫描资金尚未处理完成的调用记录
     * fixedDelay 表示上一次执行结束后再等待指定毫秒，不会与自身重叠
     */
    @Scheduled(fixedDelayString = "${router.recovery.interval-ms:300000}")
    public void scan(){

        //开关没开直接返回
        if(!recoveryProperties.isEnabled()){
            return;
        }

        //配置为零或负数时，所有在途请求都会被判定为滞留，必须拒绝
        if(recoveryProperties.getStaleSeconds() <= 0){
            log.error("巡检配置不合法，stale-seconds 必须大于零，本次跳过");

            return;
        }

        if(recoveryProperties.getBatchSize() <= 0){
            log.error("检配置不合法，batch-size 必须大于零，本次跳过");

            return;
        }

        LocalDateTime now = LocalDateTime.now();

        //复用已有的分页查询，按编号升序取最早的一批
        List<ChatCallLog> unfinished = chatCallLogMapper.selectUnfinishedBilling(0L);

        int staleCount = 0;
        int handledCount = 0;

        for(ChatCallLog record : unfinished){

            if(staleCount >= recoveryProperties.getBatchSize()){
                break;
            }

            if(record.getCreatedAt() == null){
                log.warn("滞留记录缺少创建时间，跳过处理，requestId={}", record.getRequestId());

                staleCount++;
                continue;
            }

            long ageSeconds = Duration.between(record.getCreatedAt(), now).getSeconds();

            //还没超过阈值，可能只是正在处理中
            if(ageSeconds < recoveryProperties.getStaleSeconds()){
                continue;
            }

            staleCount++;

            if("PENDING".equals(record.getBillingStatus())){
                handledCount += handlePending(record, ageSeconds);
            } else{
                //RESERVED 且超时，进程可能异常退出，这里只警告不修改
                log.warn("滞留的预留记录，可能进程异常退出，requestId={}, userId={}, frozen={}, age={}s",
                        record.getRequestId(),
                        record.getUserId(),
                        record.getReservedUnits(),
                        ageSeconds);
            }
        }

        if(staleCount > 0){
            log.warn("资金巡查完成，滞留 {} 条，自动重试 {} 条", staleCount, handledCount);
        }
    }

    /**
     * 处理待处理账单
     * 只有用量完整且未超过最大重试时长时，才尝试自动结算
     * @return 实际发起重试的次数
     */
    private int handlePending(ChatCallLog record, long ageSeconds){

        //缺少用量时无法重算费用，强行结算会得出错误的金额
        boolean usageComplete = record.getPromptTokens() != null
                && record.getCachedInputTokens() != null
                && record.getCompletionTokens() != null;

        if(!usageComplete){
            log.warn("待处理记录缺少用量，无法自动结算，requestId={}, age={}s", record.getRequestId(), ageSeconds);

            return 0;
        }

        if(!recoveryProperties.isAutoRetryEnabled()){
            log.warn("待处理记录可结算但未开启自动重试，requestId={}, age={}s", record.getRequestId(), ageSeconds);

            return 0;
        }

        if(ageSeconds > recoveryProperties.getMaxRetryAgeSeconds()){
            log.warn("待处理记录超过最大自动重试时长，requestId={}, age={}s", record.getRequestId(), ageSeconds);

            return 0;
        }

        try{
            chatBillingService.retrySettlement(record.getRequestId());
            log.info("自动重试结算完成，requestId={}, age={}s", record.getRequestId(), ageSeconds);

            return 1;
        } catch(RuntimeException exception){
            //单条失败不影响本轮其他记录
            log.warn("自动重试结算失败，requestId={}", record.getRequestId(),
                    exception);

            return 0;
        }
    }
}
