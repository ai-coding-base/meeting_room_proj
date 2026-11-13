package com.company.booking.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一响应封装
 *
 * @param <T> 业务数据类型
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {

    /** 成功固定为 "0"；失败为业务错误码 */
    private String code;

    private String msg;

    private T data;

    private String traceId;

    /**
     * 构造成功响应
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 统一响应
     */
    public static <T> R<T> ok(T data) {
        return new R<>("0", "ok", data, null);
    }

    /**
     * 构造失败响应
     *
     * @param code 业务错误码
     * @param msg  错误消息
     * @param <T>  业务数据类型
     * @return 统一响应
     */
    public static <T> R<T> fail(String code, String msg) {
        return new R<>(code, msg, null, null);
    }
}
