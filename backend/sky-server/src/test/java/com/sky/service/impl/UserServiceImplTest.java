package com.sky.service.impl;

import com.sky.dto.CustomerLoginDTO;
import com.sky.dto.CustomerRegisterDTO;
import com.sky.entity.User;
import com.sky.exception.BusinessException;
import com.sky.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    @InjectMocks private UserServiceImpl userService;

    @Test
    void registrationCreatesCustomerAndStoresOnlyBCryptHash() {
        when(userMapper.getByPhone("13812345678")).thenReturn(null);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return null;
        }).when(userMapper).insertPhoneCustomer(any(User.class));

        CustomerRegisterDTO request = new CustomerRegisterDTO();
        request.setUsername("fresh-customer");
        request.setName("鲜集顾客");
        request.setPhone("13812345678");
        request.setPassword("fresh-pass-123");

        User customer = userService.registerByPhone(request);

        assertEquals(42L, customer.getId());
        assertEquals("fresh-customer", customer.getUsername());
        assertEquals("13812345678", customer.getPhone());
        assertNotNull(customer.getCreateTime());
        assertNotEquals(request.getPassword(), customer.getPassword());
        assertEquals(true, new BCryptPasswordEncoder().matches(request.getPassword(), customer.getPassword()));
        verify(userMapper).insertPhoneCustomer(any(User.class));
    }

    @Test
    void phoneLoginRequiresMatchingPassword() {
        User stored = User.builder().id(42L).name("鲜集顾客").phone("13812345678")
                .password(new BCryptPasswordEncoder().encode("fresh-pass-123")).build();
        when(userMapper.getByPhone("13812345678")).thenReturn(stored);

        CustomerLoginDTO request = new CustomerLoginDTO();
        request.setPhone("13812345678");
        request.setPassword("fresh-pass-123");
        assertEquals(stored, userService.loginByPhone(request));

        request.setPassword("wrong-password");
        assertThrows(BusinessException.class, () -> userService.loginByPhone(request));
    }

    @Test
    void nicknameLoginChecksNicknameAndPassword() {
        User stored = User.builder().id(42L).username("fresh-customer").name("鲜集顾客")
                .password(new BCryptPasswordEncoder().encode("fresh-pass-123")).build();
        when(userMapper.getByUsername("fresh-customer")).thenReturn(stored);

        CustomerLoginDTO request = new CustomerLoginDTO();
        request.setUsername("fresh-customer");
        request.setPassword("fresh-pass-123");
        assertEquals(stored, userService.loginByNickname(request));

        request.setPassword("wrong-password");
        assertThrows(BusinessException.class, () -> userService.loginByNickname(request));
    }

    @Test
    void registrationRejectsExistingPhone() {
        when(userMapper.getByPhone("13812345678")).thenReturn(User.builder().id(12L).build());
        CustomerRegisterDTO request = new CustomerRegisterDTO();
        request.setUsername("fresh-customer");
        request.setName("顾客");
        request.setPhone("13812345678");
        request.setPassword("fresh-pass-123");
        assertThrows(BusinessException.class, () -> userService.registerByPhone(request));
    }
}
