package com.quan.userservice.auth;

public class AccountDisabledException extends RuntimeException{
    public AccountDisabledException(){
        super("Tài khoản đã bị vô hiệu hóa");
    }
}