package com.sky.interceptor;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.properties.JwtProperties;
import com.sky.entity.User;
import com.sky.mapper.UserMapper;
import com.sky.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenUserInterceptorTest {

    private static final String SECRET = "fresh-market-user-jwt-secret-key-2026";
    private final JwtTokenUserInterceptor interceptor = new JwtTokenUserInterceptor();
    private final UserMapper userMapper = mock(UserMapper.class);

    JwtTokenUserInterceptorTest() {
        JwtProperties properties = new JwtProperties();
        properties.setUserSecretKey(SECRET);
        properties.setUserTokenName("authentication");
        ReflectionTestUtils.setField(interceptor, "jwtProperties", properties);
        ReflectionTestUtils.setField(interceptor, "userMapper", userMapper);
    }

    @AfterEach
    void clearUserContext() {
        BaseContext.removeCurrentId();
    }

    @Test
    void acceptsSignedTokenAndClearsThreadContextAfterRequest() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, 88L);
        when(userMapper.getById(88L)).thenReturn(User.builder().id(88L).build());
        String token = JwtUtil.createJWT(SECRET, 60_000, claims);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("authentication", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, handler()));
        assertEquals(88L, BaseContext.getCurrentId());
        interceptor.afterCompletion(request, response, handler(), null);
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void rejectsTokenWhenCustomerAccountWasDeleted() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, 89L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("authentication", JwtUtil.createJWT(SECRET, 60_000, claims));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, handler()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsMissingOrWrongSignatureToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler()));
        assertEquals(401, response.getStatus());

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, 88L);
        request.addHeader("authentication", JwtUtil.createJWT("another-user-jwt-secret-key-2026", 60_000, claims));
        response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler()));
        assertEquals(401, response.getStatus());
    }

    private HandlerMethod handler() throws NoSuchMethodException {
        Method method = TestController.class.getMethod("endpoint");
        return new HandlerMethod(new TestController(), method);
    }

    public static class TestController {
        public void endpoint() { }
    }
}
