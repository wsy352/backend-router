package com.lingrui.router.controller;

import com.lingrui.router.dto.RouterWalletRechargeDTO;
import com.lingrui.router.entity.RouterWallet;
import com.lingrui.router.entity.RouterWalletTransaction;
import com.lingrui.router.service.RouterAuthService;
import com.lingrui.router.service.RouterWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员钱包管理接口
 * 管理员可以为用户初始化钱包，并查询可用余额和预留金额
 */
@RestController
@RequestMapping("/api/admin/users/{userId}/wallet")
public class RouterWalletController {

    //钱包管理服务：负责初始化钱包和查询钱包信息
    @Autowired
    private RouterWalletService routerWalletService;

    //身份验证服务：确保只有管理员可以操作这些接口
    @Autowired
    private RouterAuthService routerAuthService;

    /**
     * 初始化用户钱包
     * 已有钱包直接返回，重复调用不会清空余额
     * @param authorization 管理员访问秘钥
     * @param userId 用户ID
     * @return 初始化后的钱包，或已有钱包
     */
    @PutMapping
    public RouterWallet initializeWallet(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("userId") Long userId
    ){
        //先检查管理员权限，再进行钱包操作
        routerAuthService.validateAdminKey(authorization);

        return routerWalletService.getOrCreateWallet(userId);
    }

    /**
     * 查询用户钱包
     * @param authorization 管理员访问秘钥
     * @param userId 用户编号
     * @return 用户的余额，预留信息及时间信息
     */
    @GetMapping
    public RouterWallet getWallet(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("userId") Long userId
    ){
        //先检查管理员权限，再进行钱包操作
        routerAuthService.validateAdminKey(authorization);

        return routerWalletService.getByUserId(userId);
    }

    /**
     * 管理员增加用户测试余额
     * @param authorization 管理员访问秘钥
     * @param userId 用户编号
     * @param routerWalletRechargeDTO 充值金额和业务编号
     * @return 充值流水，重复提交时返回原流水
     */
    @PostMapping("/recharges")
    public RouterWalletTransaction recharge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("userId") Long userId,
            @RequestBody RouterWalletRechargeDTO routerWalletRechargeDTO
    ){

        //充值属于敏感操作，需先验证管理员身份
        routerAuthService.validateAdminKey(authorization);

        //通过注入的 Service 调用，使充值方法上的事务生效
        return routerWalletService.recharge(userId,
                routerWalletRechargeDTO.getBusinessId(),
                routerWalletRechargeDTO.getAmountUnits());
    }
}
