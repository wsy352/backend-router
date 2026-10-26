package com.lingrui.router;

import com.lingrui.router.config.RouterAuthProperties;
import com.lingrui.router.entity.RouterApiKey;
import com.lingrui.router.entity.RouterUser;
import com.lingrui.router.mapper.RouterApiKeyMapper;
import com.lingrui.router.mapper.RouterUserMapper;
import com.lingrui.router.util.ApiKeyHashUtil;
import com.lingrui.router.service.RouterAuthService;
import com.lingrui.router.service.impl.RouterAuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class RouterAuthServiceTest {

    private RouterAuthProperties routerAuthProperties;
    private RouterAuthService routerAuthService;

    @BeforeEach
    void setUp(){

        //测试使用自己的配置，不读取 IDEA 的环境变量
        routerAuthProperties = new RouterAuthProperties();
        routerAuthProperties.setAdminKey("unit-test-admin-key-not-a-secret");

        routerAuthService = new RouterAuthServiceImpl();

        // Chat keys are now resolved from the database, not configuration.
        RouterApiKeyMapper keyMapper = mock(RouterApiKeyMapper.class);
        RouterUserMapper userMapper = mock(RouterUserMapper.class);
        RouterApiKey key = new RouterApiKey();
        key.setId(3L);
        key.setUserId(1L);
        key.setStatus("ENABLED");
        RouterUser user = new RouterUser();
        user.setId(1L);
        user.setStatus("ENABLED");
        when(keyMapper.selectByKeyHash(ApiKeyHashUtil.caculateKeyHash("unit-test-chat-key-not-a-secret"))).thenReturn(key);
        when(userMapper.selectById(1L)).thenReturn(user);
        ReflectionTestUtils.setField(routerAuthService, "routerApiKeyMapper", keyMapper);
        ReflectionTestUtils.setField(routerAuthService, "routerUserMapper", userMapper);

        //没有启动 Spring ,手动将配置放入服务对象。用反射给私有字段赋值
        ReflectionTestUtils.setField(
                routerAuthService,         //目标对象，往谁身上塞
                "routerAuthProperties",    //字段名：塞进哪个字段
                routerAuthProperties       //塞什么值
        );
    }

    /**
     * 正确的秘钥应当通过验证
     */
    @Test
    void shouldAcceptCorrectKeys(){
        assertDoesNotThrow(
                () -> routerAuthService.validateChatKey("Bearer unit-test-chat-key-not-a-secret")
        );

        assertDoesNotThrow(
                () -> routerAuthService.validateAdminKey("Bearer unit-test-admin-key-not-a-secret")
        );
    }

    /**
     * 聊天秘钥和管理员秘钥不能混用
     */
    @Test
    void shouldRejectMixedKeys(){
        ResponseStatusException chatException = assertThrows(
                ResponseStatusException.class,
                () -> routerAuthService.validateChatKey("Bearer unit-test-admin-key-not-a-secret")
        );
        assertEquals(401, chatException.getStatusCode().value());

        ResponseStatusException adminException = assertThrows(
                ResponseStatusException.class,
                () -> routerAuthService.validateAdminKey("Bearer unit-test-chat-key-not-a-secret")
        );
        assertEquals(401, adminException.getStatusCode().value());
    }

    /**
     * 缺失秘钥，格式错误或秘钥错误时，应当拒绝访问
     */
    @Test
    void shouldRejectMissingOrInvalidKeys(){
        String[] invalidHeaders = {
                null,
                "",
                "unit-test-chat-key-not-a-secret",
                "Bearer ",
                "Bearer wrong"
        };

        for (String authorization : invalidHeaders){
            ResponseStatusException chatException = assertThrows(
                    ResponseStatusException.class,
                    () -> routerAuthService.validateChatKey(authorization)
            );
            assertEquals(401, chatException.getStatusCode().value());

            ResponseStatusException adminException = assertThrows(
                    ResponseStatusException.class,
                    () -> routerAuthService.validateAdminKey(authorization)
            );
            assertEquals(401, adminException.getStatusCode().value());
        }
    }

    /**
     * 服务端未配置管理员秘钥时，应当拒绝访问
     */
    @Test
    void shouldRejectUnconfiguredAdminKey(){
        routerAuthProperties.setAdminKey("");

        ResponseStatusException responseStatusException = assertThrows(
                ResponseStatusException.class,
                () -> routerAuthService.validateAdminKey("Bearer unit-test-admin-key-not-a-secret")
        );
        assertEquals(500, responseStatusException.getStatusCode().value());
    }
}
