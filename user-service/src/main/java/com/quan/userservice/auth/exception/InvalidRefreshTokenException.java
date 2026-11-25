package com.quan.userservice.auth.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
    }
}
