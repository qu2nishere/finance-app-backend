package com.quan.userservice.auth;

public class EmailAlreadyUsedException extends RuntimeException{
    public EmailAlreadyUsedException(String email){
        super("Email đã được sử dụng: " + email);
    }
}