package com.company.booking.common.exception;

/**
 * 预订时段冲突异常。
 * 触发场景：DB 唯一约束 uk_room_slot 冲突时由 Service 层转换抛出。
 *
 * @see com.company.booking.service.impl.BookingServiceImpl
 */
public class BookingConflictException extends BizException {

    public static final String CODE = "BOOKING_CONFLICT";

    public BookingConflictException(String message) {
        super(CODE, message);
    }

    public BookingConflictException(String message, Throwable cause) {
        super(CODE, message, cause);
    }
}
