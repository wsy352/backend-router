package com.lingrui.router.service.impl;

import com.lingrui.router.entity.RouterApiKey;
import com.lingrui.router.entity.RouterUser;
import com.lingrui.router.mapper.RouterApiKeyMapper;
import com.lingrui.router.service.RouterApiKeyService;
import com.lingrui.router.service.RouterUserService;
import com.lingrui.router.util.ApiKeyHashUtil;
import com.lingrui.router.vo.RouterApiKeyCreateVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * API 秘钥管理服务实现
 */
@Service
public class RouterApiKeyServiceImpl implements RouterApiKeyService {

    @Autowired
    private RouterApiKeyMapper routerApiKeyMapper;

    @Autowired
    private RouterUserService routerUserService;

    private final SecureRandom secureRandom = new SecureRandom();// 安全随机数生成器,用来生成 API 秘钥

    @Override
    public RouterApiKeyCreateVO createApiKey(Long userId, String keyName) {

        //检查用户是否存在
        RouterUser routerUser = routerUserService.getById(userId);

        //检查用户是否启用
        if(!"ENABLED".equals(routerUser.getStatus())){
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "用户已停用，不能创建秘钥"
            );
        }

        //校验秘钥名称
        if(keyName == null || keyName.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "秘钥名称不能为空"
            );
        }

        String normalizedKeyName = keyName.strip();  //去掉首位的空格

        if(normalizedKeyName.length() > 64){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "秘钥名称不能超过64个字符"
            );
        }

        //生成完整秘钥
        String apiKey = generateApiKey();
        LocalDateTime now = LocalDateTime.now();

        //保存哈希和展示信息，不保存完整秘钥
        RouterApiKey routerApiKey = new RouterApiKey();
        routerApiKey.setUserId(routerUser.getId());
        routerApiKey.setKeyName(normalizedKeyName);
        routerApiKey.setKeyHash(ApiKeyHashUtil.caculateKeyHash(apiKey));
        routerApiKey.setKeyPrefix(apiKey.substring(0, 12));
        routerApiKey.setStatus("ENABLED");
        routerApiKey.setCreatedAt(now);
        routerApiKey.setUpdatedAt(now);

        routerApiKeyMapper.insert(routerApiKey);

        //创建成功后返回完整秘钥
        RouterApiKeyCreateVO routerApiKeyCreateVO = new RouterApiKeyCreateVO();

        routerApiKeyCreateVO.setId(routerApiKey.getId());
        routerApiKeyCreateVO.setUserId(routerApiKey.getUserId());
        routerApiKeyCreateVO.setKeyName(routerApiKey.getKeyName());
        routerApiKeyCreateVO.setApiKey(apiKey);

        return routerApiKeyCreateVO;

    }

    /**
     * 使用安全随机数生成秘钥
     */
    private String generateApiKey(){
        byte[] randomBytes = new byte[32];

        //用安全随机数，把 randomBytes 这个数组填满
        secureRandom.nextBytes(randomBytes);//randomBytes 里装的是这样 32 个【字节】：[0x3f, 0xa1, 0x07, 0x9c, 0x00, ...]

        return "nr_" + HexFormat.of().formatHex(randomBytes);

    }

    /**
     * 吊销秘钥
     * 已吊销的秘钥重复操作时直接返回，保留第一次吊销事件
     * @param userId
     * @param apiKeyId
     */
    @Override
    public void revokeApiKey(Long userId, Long apiKeyId) {
        //校验路径中的两个编号
        if(userId == null || userId <= 0 || apiKeyId == null || apiKeyId <= 0){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户编号或秘钥编号必须为正整数"
            );
        }

        //查询时同时检查所属用户，避免操作其他用户的秘钥
        RouterApiKey routerApiKey = routerApiKeyMapper.selectByUserIdAndId(userId, apiKeyId);

        if(routerApiKey == null){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "该用户下不存在此秘钥"
            );
        }

        //已经停用，无需重复修改
        if("DISABLED".equals(routerApiKey.getStatus())){
            return;
        }

        //只修改状态，不删除秘钥记录
        routerApiKeyMapper.disableByUserIdAndId(userId, apiKeyId);
    }

}
