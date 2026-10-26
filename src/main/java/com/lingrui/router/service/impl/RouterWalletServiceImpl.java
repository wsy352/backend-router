package com.lingrui.router.service.impl;

import com.lingrui.router.entity.RouterWallet;
import com.lingrui.router.entity.RouterWalletTransaction;
import com.lingrui.router.mapper.RouterWalletMapper;
import com.lingrui.router.mapper.RouterWalletTransactionMapper;
import com.lingrui.router.service.RouterUserService;
import com.lingrui.router.service.RouterWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 钱包管理服务实现
 * 用户首次使用时创建零余额钱包，之后返回已有钱包
 */
@Service
public class RouterWalletServiceImpl implements RouterWalletService {

    //用户管理服务：校验用户编号，并确认用户存在
    @Autowired
    private RouterUserService routerUserService;

    //钱包数据访问接口：负责查询和保存钱包
    @Autowired
    private RouterWalletMapper routerWalletMapper;

    //资金流水数据访问接口：查询重复业务，并保存抽充值记录
    @Autowired
    private RouterWalletTransactionMapper routerWalletTransactionMapper;

    /**
     * 获取或初始化用户钱包
     * 初始化仅创建零余额记录，不执行充值或扣费
     */
    @Override
    public RouterWallet getOrCreateWallet(Long userId) {

        //先确认用户存在，避免创建没有所属用户的钱包
        routerUserService.getById(userId);

        //已有钱包直接返回，不能重新初始化余额
        RouterWallet routerWallet = routerWalletMapper.selectByUserId(userId);
        if (routerWallet != null) {
            return routerWallet;
        }

        //准备新钱包，可用余额和预留金额为 0
        LocalDateTime now = LocalDateTime.now();

        routerWallet = new RouterWallet();
        routerWallet.setUserId(userId);
        routerWallet.setBalanceUnits(0L);
        routerWallet.setFrozenUnits(0L);
        routerWallet.setCreatedAt(now);
        routerWallet.setUpdatedAt(now);

        try {
            //用户编号是主键，数据库保证一个用户只有一个钱包
            routerWalletMapper.insert(routerWallet);
        } catch (DuplicateKeyException duplicateKeyException) {
            //另一个并发请求可能已经创建了钱包，读取已有记录
            RouterWallet existingRouterWallet = routerWalletMapper.selectByUserId(userId);

            if (existingRouterWallet == null) {
                //如果没有查到钱包，保留原有异常，不能假装创建成功
                throw duplicateKeyException;
            }
            return existingRouterWallet;
        }
        return routerWallet;
    }

    /**
     * 查询用户钱包
     * 用户不存在或尚未初始化钱包时，返回明确的错误
     */
    @Override
    public RouterWallet getByUserId(Long userId) {

        //复用用户服务，检查编号是否合法，用户是否存在
        routerUserService.getById(userId);

        RouterWallet routerWallet = routerWalletMapper.selectByUserId(userId);

        if (routerWallet == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "用户钱包尚未初始化"
            );
        }

        return routerWallet;
    }

    /**
     * 增加测试余额
     * 余额更新和流水插入属于同一个事物，任何一部失败都将回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public RouterWalletTransaction recharge(
            Long userId,
            String businessId,
            Long amountUnits) {

        //限制业务编号格式，使其符合数据库ASCII字段的要求
        validateBusinessId(businessId);

        //金额统一用整数微元，不允许零或者负数充值
        if (amountUnits == null || amountUnits <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "充值金额必须为正数"
            );
        }

        //检查用户编号及用户是否存在
        //先锁定钱包，再锁定流水，保证同一钱包的充值顺序执行
        RouterWallet routerWallet = getWalletForUpdate(userId);

        //检查同一笔充值是否已经成功处理
        RouterWalletTransaction existingRouterWalletTransaction = routerWalletTransactionMapper.selectByBusiness(
                userId,
                businessId,
                "RECHARGE"
        );

        if (existingRouterWalletTransaction != null) {

            //同一业务编号不能用于两个金额不同的充值请求
            if (!amountUnits.equals(existingRouterWalletTransaction.getBalanceChangeUnits())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "该业务编号编号已用于其他充值金额"
                );
            }

            //相同业务编号，相同金额：直接返回原流水，不重复加钱
            return existingRouterWalletTransaction;
        }

        //使用精确加法，超过 Long 范围时明确拒绝
        long balanceAfterUnits;
        try {
            balanceAfterUnits = Math.addExact(
                    routerWallet.getBalanceUnits(),
                    amountUnits
            );

            //同时检查可用余额与预留金额之和，给后续释放预留金额留出空间
            Math.addExact(balanceAfterUnits, routerWallet.getFrozenUnits());
        } catch (ArithmeticException arithmeticException) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "充值后金额超出允许范围"
            );
        }

        //更新钱包余额，必须确认确实更新了一行
        int updatedRows = routerWalletMapper.increaseBalance(
                userId,
                amountUnits
        );

        if (updatedRows != 1) {
            throw new IllegalStateException("更新钱包余额失败");
        }

        //保存本次资金变化，以及操作后的余额
        return saveWalletTransaction(
                userId,
                businessId,
                "RECHARGE",
                amountUnits,
                0L,
                balanceAfterUnits,
                routerWallet.getFrozenUnits()
        );


    }

    /**
     * 为请求预留预算
     * 钱包金额更新和预留流水保存一起提交，失败时一起回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public RouterWalletTransaction reserveBudget(
            Long userId,
            String businessId,
            Long amountUnits
    ) {

        //校验请求业务编号，保持与数据库字段要求一致
        validateBusinessId(businessId);

        //预留金额必须为正整数微元
        if (amountUnits == null || amountUnits <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预留金额必须为正整数"
            );
        }

        //确认用户存在
        //锁定当前用户的钱包，按顺序处理并发资金操作
        RouterWallet routerWallet = getWalletForUpdate(userId);

        //同一个请求只能预留一次，防止重复冻结资金
        RouterWalletTransaction existingRouterWalletTransaction = routerWalletTransactionMapper.selectByBusiness(
                userId,
                businessId,
                "RESERVE"
        );

        if (existingRouterWalletTransaction != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该业务编号已用于其他预留请求"
            );
        }

        //余额不足时，在调用上游请求之前拒绝请求
        if (routerWallet.getBalanceUnits() < amountUnits) {
            throw new ResponseStatusException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "可用余额不足"
            );
        }

        //根据锁定的钱包计算操作后的余额，并检查正数溢出
        long balanceAfterUnits = routerWallet.getBalanceUnits() - amountUnits;

        long frozenAfterUnits;

        try {
            frozenAfterUnits = Math.addExact(
                    routerWallet.getFrozenUnits(),
                    amountUnits
            );
        } catch (ArithmeticException arithmeticException) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预留金额超出允许范围"
            );
        }

        //数据库再次检查余额条件，并同时更新两项金额
        int updateRows = routerWalletMapper.reserveBalance(userId, amountUnits);

        if (updateRows != 1) {
            throw new IllegalStateException("预留预算失败");
        }

        //预留时，可用余额减少，预留金额增加，总金额不变
        return saveWalletTransaction(
                userId,
                businessId,
                "RESERVE",
                -amountUnits,
                amountUnits,
                balanceAfterUnits,
                frozenAfterUnits
        );
    }

    /**
     * 全额释放请求预算
     * 从原预留流水获取金额，避免重复释放或释放其他请求的预算
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public RouterWalletTransaction releaseBudget(
            Long userId,
            String businessId
    ) {

        //校验业务编号
        validateBusinessId(businessId);

        //检查用户编号是否合法，并确认用户存在
        //先锁定钱包，再检查资金流水
        RouterWallet routerWallet = getWalletForUpdate(userId);

        //已结算的请求不能再全额释放，否则会把已消费金额退回
        RouterWalletTransaction settledWalletTransaction = routerWalletTransactionMapper.selectByBusiness(
                userId,
                businessId,
                "SETTLE"
        );

        if (settledWalletTransaction != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该业务编号已结算，不能释放预算"
            );
        }

        //已释放时返回原流水，不再次修改余额
        RouterWalletTransaction releasedWalletTransaction = routerWalletTransactionMapper.selectByBusiness(
                userId,
                businessId,
                "RELEASE"
        );

        if (releasedWalletTransaction != null) {
            return releasedWalletTransaction;
        }

        //查找这个请求自己的预留流水
        RouterWalletTransaction reservedRouterWalletTransaction = routerWalletTransactionMapper.selectByBusiness(
                userId,
                businessId,
                "RESERVE"
        );

        if (reservedRouterWalletTransaction == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "该请求没有预留记录"
            );
        }

        //原预留流水中，预留金额的增加量就是本次应释放的金额
        Long amountUnits = reservedRouterWalletTransaction.getFrozenChangeUnits();

        if (amountUnits == null || amountUnits <= 0) {
            throw new IllegalStateException("钱包预留金额与流水金额不一致");
        }

        //钱包中的预留金额必须足以覆盖这笔请求
        if (routerWallet.getFrozenUnits() < amountUnits) {
            throw new IllegalStateException("钱包预留金额与流水不一致");
        }

        long balanceAfterUnits = Math.addExact(
                routerWallet.getBalanceUnits(),
                amountUnits
        );

        long frozenAfterUnits = routerWallet.getFrozenUnits() - amountUnits;

        //恢复可用余额，减少预留金额
        int updatedRows = routerWalletMapper.releaseBalance(userId, amountUnits);

        if (updatedRows != 1) {
            throw new IllegalStateException("释放预留金额失败");
        }

        //保存释放流水，与钱包更新一起提交
        return saveWalletTransaction(
                userId,
                businessId,
                "RELEASE",
                amountUnits,
                -amountUnits,
                balanceAfterUnits,
                frozenAfterUnits
        );

    }

    /**
     * 按实际费用结算请求
     * 检查请求状态后，更新钱包并保存结算流水
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public RouterWalletTransaction settleBudget(
            Long userId,
            String businessId,
            Long actualCostUnits
    ) {
        // 校验请求业务编号
        validateBusinessId(businessId);

        // 费用可以为零，但不能为空或为负数
        if (actualCostUnits == null || actualCostUnits < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "实际费用必须为非负整数"
            );
        }

        //检查用户编号是否合法，并确认用户存在
        //先锁定钱包，再检查资金流水
        RouterWallet routerWallet = getWalletForUpdate(userId);

        //已释放的请求不能再次结算
        RouterWalletTransaction releasedRouterWalletTransaction = routerWalletTransactionMapper.selectByBusiness(
                userId,
                businessId,
                "RELEASE"
        );

        if (releasedRouterWalletTransaction != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该业务编号已释放，不能再次结算"
            );
        }

        //已结算时，检查费用是否一致
        RouterWalletTransaction settledRouterWalletTransaction = routerWalletTransactionMapper.selectByBusiness(
                userId,
                businessId,
                "SETTLE"
        );

        if (settledRouterWalletTransaction != null) {

            //两项余额变化量之和，等于实际费用的相反数
            long settledCostUnits = Math.negateExact(
                    Math.addExact(
                            settledRouterWalletTransaction.getBalanceChangeUnits(),
                            settledRouterWalletTransaction.getFrozenChangeUnits()
                    )
            );

            if (settledCostUnits != actualCostUnits.longValue()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "该请求已按其他费用结算"
                );
            }

            //相同费用重复提交，返回原流水，不重复扣款
            return settledRouterWalletTransaction;
        }

        //查询原预留记录，确定这次请求实际预留了多少钱
        RouterWalletTransaction reservedRouterWalletTransaction = routerWalletTransactionMapper.selectByBusiness(
                userId,
                businessId,
                "RESERVE"
        );
        if (reservedRouterWalletTransaction == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "该请求没有预留记录"
            );
        }

        Long reservedUnits = reservedRouterWalletTransaction.getFrozenChangeUnits();

        if (reservedUnits == null || reservedUnits <= 0) {
            throw new IllegalStateException("原预留流水金额异常");
        }

        if (routerWallet.getFrozenUnits() < reservedUnits) {
            throw new IllegalStateException("钱包预留金额与流水不一致");
        }

        //正数表示退回金额，负数表示需要补扣
        long balanceChangeUnits = reservedUnits - actualCostUnits;

        //补扣时必须保证余额足够
        if (balanceChangeUnits < 0 && routerWallet.getBalanceUnits() < -balanceChangeUnits) {
            throw new ResponseStatusException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "余额不足以补扣费用，预留金额暂不释放"
            );
        }

        long balanceAfterUnits = Math.addExact(
                routerWallet.getBalanceUnits(),
                balanceChangeUnits
        );

        long frozenAfterUnits = routerWallet.getFrozenUnits() - reservedUnits;

        //按实际费用调整钱包
        int updatedRows = routerWalletMapper.settleBalance(
                userId,
                reservedUnits,
                actualCostUnits
        );

        if (updatedRows != 1) {
            throw new IllegalStateException("按实际费用调整钱包失败");
        }

        //保存结算流水，与钱包更新一起提交
        return saveWalletTransaction(
                userId,
                businessId,
                "SETTLE",
                balanceChangeUnits,
                -reservedUnits,
                balanceAfterUnits,
                frozenAfterUnits
        );
    }

    /**
     * 校验资金操作的业务编号
     * 统一充值，预留，释放，结算的编规则
     */
    private void validateBusinessId(String businessId) {
        if (businessId == null || !businessId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "业务编号必须为1至64位字母、数字、下划线或短横线"
            );
        }
    }

    /**
     * 检查用户并锁定钱包
     * 只能在已开启的资金事务中调用，锁持续到事务结束
     */
    private RouterWallet getWalletForUpdate(Long userId) {

        routerUserService.getById(userId);

        RouterWallet routerWallet = routerWalletMapper.selectByUserIdForUpdate(userId);
        if (routerWallet == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "请先初始化用户钱包"
            );
        }
        return routerWallet;
    }

    /**
     * 构造并保存资金流水
     * 必须与对应的钱包更新处于同一个事务中
     *
     * @param userId             用户编号
     * @param businessId         业务编号
     * @param transactionType    流水类型
     * @param balanceChangeUnits 可用余额变化量
     * @param frozenChangeUnits  预留金额变化量
     * @param balanceAfterUnits  操作后的可用余额
     * @param frozenAfterUnits   操作后的预留金额
     * @return 已保存的资金流水
     */
    private RouterWalletTransaction saveWalletTransaction(
            Long userId,
            String businessId,
            String transactionType,
            long balanceChangeUnits,
            long frozenChangeUnits,
            long balanceAfterUnits,
            long frozenAfterUnits
    ) {
        RouterWalletTransaction routerWalletTransaction = new RouterWalletTransaction();
        routerWalletTransaction.setUserId(userId);
        routerWalletTransaction.setBusinessId(businessId);
        routerWalletTransaction.setTransactionType(transactionType);
        routerWalletTransaction.setBalanceChangeUnits(balanceChangeUnits);
        routerWalletTransaction.setFrozenChangeUnits(frozenChangeUnits);
        routerWalletTransaction.setBalanceAfterUnits(balanceAfterUnits);
        routerWalletTransaction.setFrozenAfterUnits(frozenAfterUnits);
        routerWalletTransaction.setCreatedAt(LocalDateTime.now());

        int insertedRows = routerWalletTransactionMapper.insert(routerWalletTransaction);

        if (insertedRows != 1) {
            throw new IllegalStateException("保存资金流水失败");
        }

        return routerWalletTransaction;
    }
}
