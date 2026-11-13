package com.company.booking.common.exception;

/**
 * 预订时间参数非法异常。
 * 触发场景：时间未对齐 30 分钟槽 / 时长 &lt; 15min / 超出提前 7 天 / 过去时间。
 */
public class InvalidTimeException extends BizException {

    public static final String CODE = "INVALID_TIME";

    public InvalidTimeException(String message) {
        super(CODE, message);
    }
}
