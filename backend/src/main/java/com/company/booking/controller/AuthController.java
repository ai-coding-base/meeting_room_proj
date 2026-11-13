package com.company.booking.controller;

import com.company.booking.common.result.R;
import com.company.booking.dto.request.LoginRequest;
import com.company.booking.dto.response.LoginResponse;
import com.company.booking.dto.response.UserInfoResponse;
import com.company.booking.security.CurrentUser;
import com.company.booking.security.LoginUser;
import com.company.booking.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权接口。
 * <p>
 * v1.0 简化版：自签名 HMAC token，无 refresh token，无登录状态服务端存储；
 * 登出由前端清 localStorage 完成。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录（无需 token，通过 WebMvcConfig 白名单放行） */
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public R<UserInfoResponse> me(@LoginUser CurrentUser user) {
        return R.ok(UserInfoResponse.builder()
                .id(user.id())
                .username(user.username())
                .displayName(user.displayName())
                .role(user.role().getValue())
                .roleLabel(user.role().getLabel())
                .build());
    }

    /**
     * 登出。
     * v1.0 服务端无状态，仅返回成功；前端负责清 token。
     */
    @PostMapping("/logout")
    public R<Void> logout(@LoginUser CurrentUser user) {
        return R.ok(null);
    }
}
