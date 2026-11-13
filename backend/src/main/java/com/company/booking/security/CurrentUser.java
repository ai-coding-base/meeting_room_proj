package com.company.booking.security;

import com.company.booking.domain.enums.Role;

/**
 * 当前登录用户的轻量上下文。
 * <p>
 * 由 AuthInterceptor 从 token 解析后放入 RequestContextHolder，
 * Controller 通过 @LoginUser 参数注解拿到。
 *
 * @param id          用户 ID
 * @param username    用户名
 * @param displayName 显示名
 * @param role        角色枚举
 */
public record CurrentUser(Long id, String username, String displayName, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isAssistant() {
        return role == Role.ASSISTANT;
    }
}
