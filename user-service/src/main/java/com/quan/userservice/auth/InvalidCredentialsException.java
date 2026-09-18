package com.quan.userservice.auth;

public class InvalidCredentialsException extends RuntimeException{
    public InvalidCredentialsException(){
        super("Email hoặc mật khẩu không hợp lệ");
    }
}