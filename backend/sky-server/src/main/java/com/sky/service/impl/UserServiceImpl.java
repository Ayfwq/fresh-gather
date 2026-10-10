package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.dto.CustomerPageQueryDTO;
import com.sky.dto.CustomerLoginDTO;
import com.sky.dto.CustomerRegisterDTO;
import com.sky.entity.User;
import com.sky.exception.BusinessException;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.result.PageResult;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import com.sky.vo.CustomerVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_-]{2,24}$");
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    //微信服务接口地址
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private UserMapper userMapper;

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    public User wxLogin(UserLoginDTO userLoginDTO) {
        String openid = getOpenid(userLoginDTO.getCode());

        //判断openid是否为空，如果为空表示登录失败，抛出业务异常
        if(openid == null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        //判断当前用户是否为新用户
        User user = userMapper.getByOpenid(openid);

        //如果是新用户，自动完成注册
        if(user == null){
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }

        //返回这个用户对象
        return user;
    }

    @Override
    public PageResult pageCustomers(CustomerPageQueryDTO query) {
        int page = query.getPage() > 0 ? query.getPage() : 1;
        int pageSize = query.getPageSize() > 0 ? Math.min(query.getPageSize(), 100) : 10;
        PageHelper.startPage(page, pageSize);
        List<CustomerVO> customers = userMapper.pageQuery(query);
        PageInfo<CustomerVO> pageInfo = new PageInfo<>(customers);
        return new PageResult(pageInfo.getTotal(), customers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User registerByPhone(CustomerRegisterDTO customer) {
        String phone = customer == null || customer.getPhone() == null ? "" : customer.getPhone().trim();
        String username = customer == null || customer.getUsername() == null ? "" : customer.getUsername().trim();
        String name = customer == null || customer.getName() == null ? "" : customer.getName().trim();
        if (username.isEmpty()) username = name;
        if (name.isEmpty()) name = username;
        String password = customer == null || customer.getPassword() == null ? "" : customer.getPassword();
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException("请输入有效的手机号");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException("昵称需为2至24位字母、汉字、数字、下划线或连字符");
        }
        if (name.length() > 30) {
            throw new BusinessException("客户姓名不能超过30个字符");
        }
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("密码至少8个字符且不能超过72个UTF-8字节");
        }
        if (userMapper.getByPhone(phone) != null) {
            throw new BusinessException("该手机号已注册，请直接登录");
        }
        if (userMapper.getByUsername(username) != null) {
            throw new BusinessException("该昵称已被使用，请换一个昵称");
        }

        User user = User.builder()
                .username(username)
                .name(name)
                .phone(phone)
                .password(PASSWORD_ENCODER.encode(password))
                .createTime(LocalDateTime.now())
                .build();
        try {
            userMapper.insertPhoneCustomer(user);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException("该手机号或昵称已注册，请检查后重试");
        }
        return user;
    }

    @Override
    public User loginByPhone(CustomerLoginDTO customer) {
        String phone = customer == null || customer.getPhone() == null ? "" : customer.getPhone().trim();
        String password = customer == null || customer.getPassword() == null ? "" : customer.getPassword();
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException("手机号或密码错误");
        }
        User user = userMapper.getByPhone(phone);
        if (user == null || user.getPassword() == null || !PASSWORD_ENCODER.matches(password, user.getPassword())) {
            throw new BusinessException("手机号或密码错误");
        }
        return user;
    }

    @Override
    public User loginByNickname(CustomerLoginDTO customer) {
        String username = customer == null || customer.getUsername() == null ? "" : customer.getUsername().trim();
        String password = customer == null || customer.getPassword() == null ? "" : customer.getPassword();
        if (username.isEmpty() || password.isEmpty()) {
            throw new BusinessException("昵称或密码错误");
        }

        User user = userMapper.getByUsername(username);
        if (user != null && user.getPassword() != null && PASSWORD_ENCODER.matches(password, user.getPassword())) {
            return user;
        }

        // Keep accounts created before nickname login available through their original phone or display name.
        User byPhone = PHONE_PATTERN.matcher(username).matches() ? userMapper.getByPhone(username) : null;
        if (byPhone != null && byPhone.getPassword() != null && PASSWORD_ENCODER.matches(password, byPhone.getPassword())) {
            return byPhone;
        }
        for (User byName : userMapper.getByName(username)) {
            if (byName.getPassword() != null && PASSWORD_ENCODER.matches(password, byName.getPassword())) {
                return byName;
            }
        }
        throw new BusinessException("昵称或密码错误");
    }

    /**
     * 调用微信接口服务，获取微信用户的openid
     * @param code
     * @return
     */
    private String getOpenid(String code){
        //调用微信接口服务，获得当前微信用户的openid
        Map<String, String> map = new HashMap<>();
        map.put("appid",weChatProperties.getAppid());
        map.put("secret",weChatProperties.getSecret());
        map.put("js_code",code);
        map.put("grant_type","authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN, map);

        JSONObject jsonObject = JSON.parseObject(json);
        String openid = jsonObject.getString("openid");
        return openid;
    }
}
