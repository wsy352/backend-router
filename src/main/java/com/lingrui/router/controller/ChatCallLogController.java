package com.lingrui.router.controller;

import com.lingrui.router.dto.ChatBillingReleaseDTO;
import com.lingrui.router.entity.ChatCallLog;
import com.lingrui.router.mapper.ChatCallLogMapper;
import com.lingrui.router.service.ChatBillingService;
import com.lingrui.router.service.RouterAuthService;
import com.lingrui.router.vo.ChatCallStatsVO;
import com.lingrui.router.vo.RouterCacheStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 查询调用记录，当前用于本地测试
 */
@RestController
@RequestMapping("/api/call-logs")
public class ChatCallLogController {

    @Autowired
    private ChatCallLogMapper chatCallLogMapper;

    @Autowired
    private RouterAuthService routerAuthService;

    //聊天资金服务：使用一保存的用量和价格，重试未完成的结算
    @Autowired
    private ChatBillingService chatBillingService;

    /**
     * 查询最近20条调用记录
     */
    @GetMapping
    public List<ChatCallLog> listRecent(@RequestHeader(value = "Authorization", required = false) String authorization) {

        routerAuthService.validateAdminKey(authorization);

        return chatCallLogMapper.selectRecent();
    }

    /**
     * 查询全部调用记录的数量统计
     */
    @GetMapping("/stats")
    public ChatCallStatsVO getStats(@RequestHeader(value = "Authorization", required = false) String authorization){

        routerAuthService.validateAdminKey(authorization);

        return chatCallLogMapper.selectStats();
    }

    /**
     * 管理员分页查看资金尚未处理完成的请求
     * @param authorization 管理员访问秘钥
     * @param afterId 上一页最后一条记录的编号，默认从头查询
     * @return 最多 50 条记录
     */
    @GetMapping("/billing/unfinished")
    public List<ChatCallLog> listUnfinishedBilling(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "afterId", defaultValue = "0") Long afterId){

        //调用记录包含用户身份和费用信息，只允许管理员查看
        routerAuthService.validateAdminKey(authorization);

        if(afterId == null || afterId < 0){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "afterId 必须为非负整数"
            );
        }

        return chatCallLogMapper.selectUnfinishedBilling(afterId);
    }

    /**
     * 管理员重试指定请求的结算，不重新调用上游模型
     * @param authorization 管理员访问秘钥
     * @param requestId 调用记录中的请求编号
     * @return 最新的结算结果
     */
    @PostMapping("/{requestId}/billing/retry")
    public ChatCallLog retryBilling(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("requestId") String requestId){

        //重试可能实际扣款，必须先验证管理员身份
        routerAuthService.validateAdminKey(authorization);

        return chatBillingService.retrySettlement(requestId);
    }

    /**
    * 管理员核对后释放失败请求的预留资金
    */
    @PostMapping("/{requestId}/billing/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseBilling(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("requestId") String requestId,
            @RequestBody ChatBillingReleaseDTO chatBillingReleaseDTO
    ){

        //释放会改变钱包余额，必须先校验管理员身份
        routerAuthService.validateAdminKey(authorization);

        if(chatBillingReleaseDTO == null){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "请求体不能为空"
            );
        }

        chatBillingService.releasePending(
                chatBillingReleaseDTO.getUserId(),
                requestId,
                chatBillingReleaseDTO.getReason()
        );
    }

    /**
     * 管理员查询按 key，渠道和模型别名汇总的缓存与费用
     * 返回当前数据库中全部历史成功调用的统计结果
     */
    @GetMapping("/cache-stats")
    public List<RouterCacheStatsVO> getCacheStats(
            @RequestHeader(value = "Authorization", required = false)
            String authorization
    ){

        //统计包含不同用户的用量与费用，只允许管理员查看
        routerAuthService.validateAdminKey(authorization);

        return chatCallLogMapper.selectCacheStats();
    }
}
