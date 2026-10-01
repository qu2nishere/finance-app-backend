package com.quan.userservice.common.exception;

import com.quan.userservice.auth.exception.EmailAlreadyUsedException;
import com.quan.userservice.auth.exception.AccountDisabledException;
import com.quan.userservice.auth.exception.AccountLockedException;
import com.quan.userservice.auth.exception.InvalidCredentialsException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler{

    // 400 — @Valid that bai
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex){
        Map<String, String> errors = new LinkedHashMap<>();
        for(FieldError fe : ex.getBindingResult().getFieldErrors()){
            // 1 field co the vi pham nhieu luat cung luc (@Email + @Pattern) -> giu 1 message
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ");
        pd.setProperty("errors", errors);
        return pd;
    }

    // 409 — email da ton tai
    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailUsed(EmailAlreadyUsedException ex){
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
    
    // 401 — sai thong tin dang nhap
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex){
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // 423 — tam khoa
    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail handleLocked(AccountLockedException ex){
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, ex.getMessage());
        pd.setProperty("lockedUntil", ex.getLockedUntil());
        return pd;
    }

    // 403 — tai khoan bi vo hieu hoa
    @ExceptionHandler(AccountDisabledException.class)
    public ProblemDetail handleDisabled(AccountDisabledException ex){
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }
}