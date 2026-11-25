package com.quan.userservice.auth.exception;

public class InvalidOtpException extends RuntimeException {

    public InvalidOtpException() {
        super("Mã xác nhận không đúng hoặc đã hết hạn");
    }
}
