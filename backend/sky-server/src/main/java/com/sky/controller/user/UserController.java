package com.sky.controller.user;

import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.dto.CustomerLoginDTO;
import com.sky.dto.CustomerRegisterDTO;
import com.sky.entity.User;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/user")
@Api(tags = "C端用户相关接口")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation("微信登录")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO){
        log.info("微信用户登录请求");

        //微信登录
        User user = userService.wxLogin(userLoginDTO);

        return createLoginResult(user);
    }

    @PostMapping("/register")
    @ApiOperation("手机号注册客户")
    public Result<UserLoginVO> register(@RequestBody CustomerRegisterDTO customer) {
        return createLoginResult(userService.registerByPhone(customer));
    }

    @PostMapping("/phone-login")
    @ApiOperation("手机号密码登录")
    public Result<UserLoginVO> phoneLogin(@RequestBody CustomerLoginDTO customer) {
        return createLoginResult(userService.loginByPhone(customer));
    }

    @PostMapping("/nickname-login")
    @ApiOperation("昵称密码登录客户")
    public Result<UserLoginVO> nicknameLogin(@RequestBody CustomerLoginDTO customer) {
        return createLoginResult(userService.loginByNickname(customer));
    }

    private Result<UserLoginVO> createLoginResult(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID,user.getId());
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .name(user.getName())
                .openid(user.getOpenid())
                .token(token)
                .build();
        return Result.success(userLoginVO);
    }
}
