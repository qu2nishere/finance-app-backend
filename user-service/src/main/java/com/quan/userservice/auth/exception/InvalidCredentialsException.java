package com.quan.userservice.auth.exception;

public class InvalidCredentialsException extends RuntimeException{
    public InvalidCredentialsException(){
        super("Email hoặc mật khẩu không hợp lệ");
    }

    public InvalidCredentialsException(String message){
        super(message);
    }
}