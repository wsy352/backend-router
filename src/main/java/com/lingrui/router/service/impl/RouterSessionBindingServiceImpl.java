package com.lingrui.router.service.impl;

import com.lingrui.router.entity.RouterSessionBinding;
import com.lingrui.router.mapper.RouterSessionBindingMapper;
import com.lingrui.router.service.RouterSessionBindingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 会话绑定服务实现
 * 校验会话标识，查询历史渠道，并保存绑定
 */
@Service
public class RouterSessionBindingServiceImpl implements RouterSessionBindingService {

    //会话绑定数据访问接口：负责读取和保存数据库记录
    @Autowired
    private RouterSessionBindingMapper routerSessionBindingMapper;

    //绑定有效期，单位秒；未配置时默认使用 30 分钟
    @Value("${router.session-binding.seconds:1800}")
    private long ttlSeconds;

    @Override
    public String findChannel(
            Long apiKeyId,
            String modelAlias,
            String sessionId) {

        //没有传入会话编号时，允许继续使用普通路由
        if(sessionId == null){
            return null;
        }

        validateBingKey(apiKeyId, modelAlias, sessionId);

        RouterSessionBinding routerSessionBinding = routerSessionBindingMapper.selectActive(
                apiKeyId,
                modelAlias,
                sessionId);

        if(routerSessionBinding == null){
            return null;
        }

        return routerSessionBinding.getChannel();
    }

    @Override
    public void saveBinding(
            Long apiKeyId,
            String modelAlias,
            String sessionId,
            String channel) {

        //未使用会话功能的请求，不创建绑定记录
        if (sessionId == null) {
            return;
        }

        validateBingKey(apiKeyId, modelAlias, sessionId);

        //渠道由服务端选择，异常值属于内部调用错误
        if(channel == null || !channel.matches("[a-zA-Z0-9_-]{1,64}")){
            throw new IllegalArgumentException("渠道标识不合法");
        }

        //限制配置范围，避免零有效期或过大的时间间隔
        if(ttlSeconds <= 0 || ttlSeconds > 604800){
            throw new IllegalStateException("会话绑定有效期必须在 1 至 604800 秒之间");
        }

        routerSessionBindingMapper.saveBinding(
                apiKeyId,
                modelAlias,
                sessionId,
                channel,
                ttlSeconds
        );
    }

    /**
     * 校验唯一定位一条绑定所需的信息
     * 会话编号约定为字母，数字，下划线和短横线
     */
    private void validateBingKey(
            Long apiKeyId,
            String modelAlias,
            String sessionId) {

        //秘钥编号必须来自鉴权结果，不能由调用者自行指定
        if(apiKeyId == null || apiKeyId <= 0){
            throw new IllegalArgumentException("秘钥编号不合法");
        }

        if(modelAlias == null || modelAlias.isBlank() || modelAlias.length() > 128){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "模型别名不能为空且长度不能超过128"
            );
        }

        if(!sessionId.matches("[A-Za-z0-9_-]{1,128}")){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "会话编号必须为1至128位字母、数字、下划线或短横线"
            );
        }
    }
}
