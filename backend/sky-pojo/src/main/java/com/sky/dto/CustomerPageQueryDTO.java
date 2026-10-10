package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CustomerPageQueryDTO implements Serializable {
    private String name;
    private String phone;
    private int page = 1;
    private int pageSize = 10;
}
