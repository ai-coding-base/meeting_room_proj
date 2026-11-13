package com.company.booking.security;

/**
 * 当前请求线程的用户上下文（ThreadLocal）。
 * <p>
 * 由 AuthInterceptor 在 preHandle 写入，afterCompletion 清理；
 * Controller / Service 通过 get() 拿到，避免到处传 CurrentUser。
 */
public final class UserContextHolder {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
