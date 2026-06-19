package com.clouddisk.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BlockException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiErrorResponse handleBlock(BlockException e, HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试", "RATE_LIMITED", request);
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleBusiness(BusinessException e, HttpServletRequest request) {
        log.debug("业务异常 path={} code={}: {}", request.getRequestURI(), e.getCode(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), e.getCode(), request);
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleNotLogin(NotLoginException e, HttpServletRequest request) {
        log.debug("未登录访问 path={}", request.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "未登录或登录已过期", "UNAUTHORIZED", request);
    }

    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleNotPermission(NotPermissionException e, HttpServletRequest request) {
        log.debug("权限不足 path={}", request.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "没有权限执行此操作", "FORBIDDEN", request);
    }

    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleNotRole(NotRoleException e, HttpServletRequest request) {
        log.debug("角色不足 path={}", request.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "没有权限执行此操作", "FORBIDDEN", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getDefaultMessage() : "参数校验失败";
        return build(HttpStatus.BAD_REQUEST, msg, "VALIDATION_ERROR", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        if (msg.isBlank()) msg = "参数校验失败";
        return build(HttpStatus.BAD_REQUEST, msg, "VALIDATION_ERROR", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "缺少必要参数: " + e.getParameterName(), "MISSING_PARAM", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "参数类型错误: " + e.getName(), "TYPE_MISMATCH", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.debug("请求体解析失败 path={}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "请求体格式错误", "BAD_REQUEST_BODY", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiErrorResponse handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "不支持该请求方式", "METHOD_NOT_ALLOWED", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiErrorResponse handleMediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的请求内容类型", "UNSUPPORTED_MEDIA_TYPE", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMaxUpload(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "上传文件超过大小限制", "FILE_TOO_LARGE", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "请求的资源不存在", "NOT_FOUND", request);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleOther(Exception e, HttpServletRequest request) {
        log.error("未处理异常 path={}", request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试", "INTERNAL_ERROR", request);
    }

    private ApiErrorResponse build(HttpStatus status, String message, String code, HttpServletRequest request) {
        return ApiErrorResponse.of(status.value(), message, code, request.getRequestURI());
    }
}
