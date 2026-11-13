package com.company.booking.service;

import com.company.booking.dto.request.LoginRequest;
import com.company.booking.dto.response.LoginResponse;

/**
 * 鉴权服务。
 */
public interface AuthService {

    /**
     * 用户名密码登录，返回 token + 用户信息。
     *
     * @throws com.company.booking.common.exception.UnauthorizedException 用户不存在/已停用/密码错误
     */
    LoginResponse login(LoginRequest request);
}
