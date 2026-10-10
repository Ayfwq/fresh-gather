package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CustomerLoginDTO implements Serializable {
    private String username;
    private String phone;
    private String password;
}
