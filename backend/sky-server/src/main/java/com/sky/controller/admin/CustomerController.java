package com.sky.controller.admin;

import com.sky.dto.CustomerPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/customer")
@Api(tags = "客户档案管理")
public class CustomerController {

    @Autowired
    private UserService userService;

    @GetMapping("/page")
    @ApiOperation("客户档案分页检索")
    public Result<PageResult> page(CustomerPageQueryDTO query) {
        return Result.success(userService.pageCustomers(query));
    }
}
