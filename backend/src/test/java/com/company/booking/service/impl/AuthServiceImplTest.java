package com.company.booking.service.impl;

import com.company.booking.common.exception.UnauthorizedException;
import com.company.booking.domain.entity.UserEntity;
import com.company.booking.domain.enums.Role;
import com.company.booking.dto.request.LoginRequest;
import com.company.booking.dto.response.LoginResponse;
import com.company.booking.mapper.UserMapper;
import com.company.booking.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 单元测试。
 * <p>
 * 覆盖：用户不存在、未激活、密码错误、登录成功；并验证统一错误提示防止用户名枚举。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String AUTH_FAIL_MSG = "用户名或密码错误";

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    private LoginRequest req;

    @BeforeEach
    void setUp() {
        req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("password123");
    }

    @Test
    @DisplayName("用户不存在 → 抛 UnauthorizedException，统一提示，不调用 tokenService")
    void login_userNotFound() {
        when(userMapper.selectByUsername("alice")).thenReturn(null);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AUTH_FAIL_MSG);

        verify(tokenService, never()).issue(anyLong(), any());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("用户被禁用 (isActive=false) → 抛 UnauthorizedException，不校验密码")
    void login_inactiveUser() {
        UserEntity user = buildUser(1L, "alice", "$bcrypt-hash", Role.EMPLOYEE);
        user.setIsActive(false);
        when(userMapper.selectByUsername("alice")).thenReturn(user);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AUTH_FAIL_MSG);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(tokenService, never()).issue(anyLong(), any());
    }

    @Test
    @DisplayName("密码错误 → 抛 UnauthorizedException，统一提示，不签发 token")
    void login_wrongPassword() {
        UserEntity user = buildUser(1L, "alice", "$bcrypt-hash", Role.EMPLOYEE);
        when(userMapper.selectByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches("password123", "$bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AUTH_FAIL_MSG);

        verify(tokenService, never()).issue(anyLong(), any());
    }

    @Test
    @DisplayName("登录成功 → 返回 token + UserInfo，并调用 tokenService.issue")
    void login_success() {
        UserEntity user = buildUser(7L, "alice", "$bcrypt-hash", Role.MANAGER);
        when(userMapper.selectByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches("password123", "$bcrypt-hash")).thenReturn(true);
        when(tokenService.issue(7L, Role.MANAGER)).thenReturn("issued-token");

        LoginResponse res = authService.login(req);

        assertThat(res).isNotNull();
        assertThat(res.getToken()).isEqualTo("issued-token");
        assertThat(res.getUser().getId()).isEqualTo(7L);
        assertThat(res.getUser().getUsername()).isEqualTo("alice");
        assertThat(res.getUser().getDisplayName()).isEqualTo("Alice");
        assertThat(res.getUser().getRole()).isEqualTo("MANAGER");
        assertThat(res.getUser().getRoleLabel()).isEqualTo("部门主管");

        verify(tokenService).issue(7L, Role.MANAGER);
    }

    @Test
    @DisplayName("isActive=null（默认未设置）按照 !FALSE 处理 → 视为激活，可登录成功")
    void login_isActiveNull_treatedAsActive() {
        UserEntity user = buildUser(2L, "bob", "$h", Role.EMPLOYEE);
        user.setIsActive(null);
        when(userMapper.selectByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenService.issue(2L, Role.EMPLOYEE)).thenReturn("t");

        LoginResponse res = authService.login(req);

        assertThat(res.getToken()).isEqualTo("t");
    }

    private UserEntity buildUser(Long id, String username, String hash, Role role) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername(username);
        u.setDisplayName(username.substring(0, 1).toUpperCase() + username.substring(1));
        u.setPasswordHash(hash);
        u.setRole(role);
        u.setIsActive(true);
        return u;
    }
}
