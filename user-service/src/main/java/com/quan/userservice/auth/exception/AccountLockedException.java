package com.quan.userservice.auth.exception;

import java.time.Instant;
// AccountLockedException.java — 423
public class AccountLockedException extends RuntimeException{
    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil){
        super("Tài khoản tạm khóa do nhập sai mật khẩu quá nhiều lần");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil(){
        return lockedUntil;
    }
}
