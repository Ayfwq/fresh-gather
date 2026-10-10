package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CustomerRegisterDTO implements Serializable {
    private String username;
    private String name;
    private String phone;
    private String password;
}
