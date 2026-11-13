package com.company.booking.service.impl;

import com.company.booking.common.exception.UnauthorizedException;
import com.company.booking.domain.entity.UserEntity;
import com.company.booking.dto.request.LoginRequest;
import com.company.booking.dto.response.LoginResponse;
import com.company.booking.dto.response.UserInfoResponse;
import com.company.booking.mapper.UserMapper;
import com.company.booking.security.TokenService;
import com.company.booking.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 鉴权服务实现。
 * <p>
 * 安全要点：
 * <ul>
 *   <li>用户不存在与密码错误返回相同提示，防止用户名枚举</li>
 *   <li>密码使用 BCrypt 校验，不存明文</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    /** 统一错误提示，避免泄露用户是否存在 */
    private static final String AUTH_FAIL = "用户名或密码错误";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Override
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userMapper.selectByUsername(request.getUsername());
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            log.info("login failed (user not found or inactive): username={}", request.getUsername());
            throw new UnauthorizedException(AUTH_FAIL);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.info("login failed (bad password): userId={}", user.getId());
            throw new UnauthorizedException(AUTH_FAIL);
        }

        String token = tokenService.issue(user.getId(), user.getRole());
        log.info("login ok: userId={}, role={}", user.getId(), user.getRole());

        return LoginResponse.builder()
                .token(token)
                .user(UserInfoResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .role(user.getRole().getValue())
                        .roleLabel(user.getRole().getLabel())
                        .build())
                .build();
    }
}
