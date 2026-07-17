package com.mikle.syncup.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import com.mikle.syncup.common.BaseResponse;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse businessExceptionHandler(BusinessException e) {
        log.error("businessException: " + e.getMessage(), e);
        return ResultUtils.error(e.getCode(), e.getMessage(), e.getDescription());
    }

    @ExceptionHandler(NotLoginException.class)
    public BaseResponse notLoginExceptionHandler(NotLoginException e) {
        log.error("notLoginException: " + e.getMessage(), e);
        return ResultUtils.error(ErrorCode.NOT_LOGIN, e.getMessage(), "");
    }

    @ExceptionHandler(SaTokenException.class)
    public BaseResponse saTokenExceptionHandler(SaTokenException e) {
        log.error("saTokenException: " + e.getMessage(), e);
        return ResultUtils.error(ErrorCode.NO_AUTH, e.getMessage(), "");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public BaseResponse validationExceptionHandler(Exception e) {
        String message = "请求参数校验失败";
        if (e instanceof MethodArgumentNotValidException validationException
                && validationException.getBindingResult().getFieldError() != null) {
            message = validationException.getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof BindException bindException
                && bindException.getBindingResult().getFieldError() != null) {
            message = bindException.getBindingResult().getFieldError().getDefaultMessage();
        }
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, message, "");
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse runtimeExceptionHandler(RuntimeException e) {
        log.error("runtimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, e.getMessage(), "");
    }
}
