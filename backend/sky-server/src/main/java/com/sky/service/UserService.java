package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.dto.CustomerPageQueryDTO;
import com.sky.dto.CustomerLoginDTO;
import com.sky.dto.CustomerRegisterDTO;
import com.sky.entity.User;
import com.sky.result.PageResult;

public interface UserService {

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    User wxLogin(UserLoginDTO userLoginDTO);

    PageResult pageCustomers(CustomerPageQueryDTO query);

    User registerByPhone(CustomerRegisterDTO customer);

    User loginByPhone(CustomerLoginDTO customer);

    User loginByNickname(CustomerLoginDTO customer);
}
