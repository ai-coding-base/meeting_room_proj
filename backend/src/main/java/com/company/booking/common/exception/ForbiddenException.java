package com.company.booking.common.exception;

/**
 * 已认证但无权限（HTTP 403）。
 */
public class ForbiddenException extends BizException {

    public static final String CODE = "FORBIDDEN";

    public ForbiddenException(String message) {
        super(CODE, message);
    }
}
