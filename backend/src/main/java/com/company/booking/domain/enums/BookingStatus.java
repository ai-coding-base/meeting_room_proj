package com.company.booking.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 预订状态。
 * 详见 docs/schema.md §3.1。
 */
public enum BookingStatus {

    /** 待审批（仅 VIP/大型会议室） */
    PENDING("PENDING"),

    /** 已确认 */
    CONFIRMED("CONFIRMED"),

    /** 审批驳回（终态） */
    REJECTED("REJECTED"),

    /** 已取消 */
    CANCELLED("CANCELLED"),

    /** 已结束（系统自动流转） */
    COMPLETED("COMPLETED");

    @EnumValue
    private final String value;

    BookingStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
