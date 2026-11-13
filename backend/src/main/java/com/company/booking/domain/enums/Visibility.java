package com.company.booking.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 预订可见性。
 * <p>
 * 详见 docs/prd.md §4.5。
 */
public enum Visibility {

    /** 全员可见（默认） */
    PUBLIC("PUBLIC"),

    /** 仅自己可见 */
    PRIVATE("PRIVATE");

    @EnumValue
    private final String value;

    Visibility(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
