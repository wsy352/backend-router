package com.lingrui.router.service.impl;

import com.lingrui.router.config.ModelPriceProperties;
import com.lingrui.router.service.BillingService;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * 费用计算服务实现
 * 使用精确整数运算，避免浮点误差和中间乘法溢出
 */
@Service
public class BillingServiceImpl implements BillingService {

    //配置中的单价以百万 token 为计价基数
    private static final BigInteger TOKENS_PER_MILLION = BigInteger.valueOf(1_000_000L);

    @Override
    public long calculateCost(
            Long promptTokens,
            Long cachedInputTokens,
            Long completionTokens,
            ModelPriceProperties modelPriceProperties
    ){

        //缺少用量时不能擅自按 0 计费
        validateTokenCount(promptTokens, "输入");
        validateTokenCount(cachedInputTokens, "缓存命中输入");
        validateTokenCount(completionTokens, "输出");

        //缓存命中量是输入总量的一部分，不能超过总量
        if(cachedInputTokens > promptTokens){
            throw new IllegalArgumentException("缓存命中输入量不能超过输入总量");
        }

        //价格缺失属于服务端配置问题，不能默认免费
        validatePrice(modelPriceProperties);

        long ordinaryInputTokens = promptTokens - cachedInputTokens;

        //分别计算三部分费用的分子，暂时不除以百万
        BigInteger ordinaryInputNumerator = multiply(
                ordinaryInputTokens,
                modelPriceProperties.getInputUnitsPerMillion()
        );

        BigInteger cachedInputNumerator = multiply(
                cachedInputTokens,
                modelPriceProperties.getCachedInputUnitsPerMillion()
        );

        BigInteger outputNumerator = multiply(
                completionTokens,
                modelPriceProperties.getOutputUnitsPerMillion()
        );

        //先合计，再统一取整，避免每一部分取整造成额外误差
        BigInteger totalNumerator = ordinaryInputNumerator
                .add(cachedInputNumerator)
                .add(outputNumerator);

        //对非负整数执行向上取整：（分子 + 除数 - 1）/除数
        BigInteger costUnits = totalNumerator.add(TOKENS_PER_MILLION.subtract(BigInteger.ONE)).divide(TOKENS_PER_MILLION);

        //超过钱包 Long 范围时抛出异常，不能截断成错误金额
        return costUnits.longValueExact();
    }

    /**
     * 校验 token 数量，拒绝缺失或负数用量
     */
    private void validateTokenCount(Long tokenCount, String tokenType){
        if(tokenCount == null || tokenCount < 0){
            throw new IllegalArgumentException(tokenType + "token 数不能为空或负数");
        }
    }

    /**
     * 校验价格配置，零价格允许，缺失和负价格不允许
     */
    private void validatePrice(ModelPriceProperties modelPriceProperties){
        if(modelPriceProperties == null){
            throw new IllegalStateException("模型价格尚未配置");
        }

        String version = modelPriceProperties.getVersion();

        if(version == null || version.isBlank()){
            throw new IllegalStateException("模型价格版本号不能为空");
        }

        validateUnitPrice(modelPriceProperties.getInputUnitsPerMillion(), "普通输入");
        validateUnitPrice(modelPriceProperties.getCachedInputUnitsPerMillion(), "缓存命中输入");
        validateUnitPrice(modelPriceProperties.getOutputUnitsPerMillion(), "输出");

    }

    /**
     * 检查单项价格是否完整，合法
     */
    private void validateUnitPrice(Long unitPrice, String priceType){
        if(unitPrice == null || unitPrice < 0){
            throw new IllegalStateException(priceType + "价格不能为空或为负数");
        }
    }

    /**
     * 先转换成 BigInteger 再相乘，避免 long 乘法提前溢出
     */
    private BigInteger multiply(long tokenCount, long unitPrice){
        return BigInteger.valueOf(tokenCount).multiply(BigInteger.valueOf(unitPrice));
    }

}
