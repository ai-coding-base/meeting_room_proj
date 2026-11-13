package com.company.booking.common.exception;

/**
 * 未认证（HTTP 401）。
 */
public class UnauthorizedException extends BizException {

    public static final String CODE = "UNAUTHORIZED";

    public UnauthorizedException(String message) {
        super(CODE, message);
    }
}
