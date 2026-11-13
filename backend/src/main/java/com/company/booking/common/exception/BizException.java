package com.company.booking.common.exception;

import lombok.Getter;

/**
 * 业务异常基类。
 * 所有业务异常必须继承本类，禁止直接抛 RuntimeException。
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
