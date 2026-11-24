package com.netguard.exception;

import com.netguard.common.Result;
import com.netguard.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理器：接口请求统一返回 JSON，页面请求渲染错误页。
 *
 * <p>区分 /api 前缀（前端轮询接口）与普通页面请求，避免异常时返回 JSON 导致页面白屏。</p>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：参数校验失败、库存不足等可预期错误。
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [{}] {}：{}", request.getMethod(), request.getRequestURI(), e.getMessage());
        if (isAjax(request)) {
            return Result.fail(e.getCode(), e.getMessage());
        }
        return errorView(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * 表单/JSON 参数校验失败。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Object handleValidationException(BindException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败 [{}]：{}", request.getRequestURI(), message);
        if (isAjax(request)) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
        }
        return errorView(message, HttpStatus.BAD_REQUEST);
    }

    /**
     * 缺少必填请求参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        String message = "缺少必要参数：" + e.getParameterName();
        return isAjax(request) ? Result.fail(ResultCode.BAD_REQUEST.getCode(), message)
                : errorView(message, HttpStatus.BAD_REQUEST);
    }

    /**
     * 上传文件超过限制。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUpload(MaxUploadSizeExceededException e, HttpServletRequest request) {
        String message = "上传文件过大，图片请控制在 5MB 以内";
        return isAjax(request) ? Result.fail(ResultCode.FILE_UPLOAD_ERROR.getCode(), message)
                : errorView(message, HttpStatus.BAD_REQUEST);
    }

    /**
     * 越权访问：已登录但角色不足。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("越权访问被拦截：{} {}", request.getMethod(), request.getRequestURI());
        if (isAjax(request)) {
            return Result.fail(ResultCode.FORBIDDEN);
        }
        ModelAndView mv = new ModelAndView("error/403");
        mv.addObject("message", ResultCode.FORBIDDEN.getMessage());
        return mv;
    }

    /**
     * 请求方法不支持。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return isAjax(request) ? Result.fail(ResultCode.BAD_REQUEST.getCode(), "请求方法不支持")
                : errorView("请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * 兜底异常：记录完整堆栈，页面只展示友好提示。
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{}] {}", request.getMethod(), request.getRequestURI(), e);
        if (isAjax(request)) {
            return Result.fail(ResultCode.BUSINESS_ERROR.getCode(), "服务器开小差了，请稍后重试");
        }
        ModelAndView mv = new ModelAndView("error/500");
        mv.addObject("message", "服务器开小差了，请稍后重试");
        mv.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return mv;
    }

    private ModelAndView errorView(String message, HttpStatus status) {
        ModelAndView mv = new ModelAndView("error/500");
        mv.addObject("message", message);
        mv.setStatus(status);
        return mv;
    }

    /**
     * 判断是否为接口请求：/api 前缀或 AJAX 请求头。
     */
    private boolean isAjax(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return true;
        }
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }
}
