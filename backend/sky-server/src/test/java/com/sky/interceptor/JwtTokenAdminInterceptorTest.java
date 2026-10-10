package com.sky.interceptor;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.entity.Employee;
import com.sky.mapper.EmployeeMapper;
import com.sky.properties.JwtProperties;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenAdminInterceptorTest {

    private static final String SECRET = "fresh-market-admin-jwt-secret-key-2026";
    private final JwtTokenAdminInterceptor interceptor = new JwtTokenAdminInterceptor();
    private final EmployeeMapper employeeMapper = mock(EmployeeMapper.class);

    JwtTokenAdminInterceptorTest() {
        JwtProperties properties = new JwtProperties();
        properties.setAdminSecretKey(SECRET);
        properties.setAdminTokenName("token");
        ReflectionTestUtils.setField(interceptor, "jwtProperties", properties);
        ReflectionTestUtils.setField(interceptor, "employeeMapper", employeeMapper);
    }

    @AfterEach
    void clearUserContext() {
        BaseContext.removeCurrentId();
    }

    @Test
    void acceptsTokenForActiveEmployee() throws Exception {
        when(employeeMapper.getById(77L)).thenReturn(Employee.builder().id(77L).status(1).build());
        MockHttpServletRequest request = requestWithEmployeeToken(77L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, handler()));
        assertEquals(77L, BaseContext.getCurrentId());
    }

    @Test
    void rejectsTokenWhenEmployeeAccountWasDeleted() throws Exception {
        MockHttpServletRequest request = requestWithEmployeeToken(78L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, handler()));
        assertEquals(401, response.getStatus());
    }

    private MockHttpServletRequest requestWithEmployeeToken(long employeeId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employeeId);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("token", JwtUtil.createJWT(SECRET, 60_000, claims));
        return request;
    }

    private HandlerMethod handler() throws NoSuchMethodException {
        Method method = TestController.class.getMethod("endpoint");
        return new HandlerMethod(new TestController(), method);
    }

    public static class TestController {
        public void endpoint() { }
    }
}
