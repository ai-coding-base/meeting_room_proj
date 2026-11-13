package com.company.booking.security;

import com.company.booking.common.exception.UnauthorizedException;
import com.company.booking.domain.entity.UserEntity;
import com.company.booking.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：从 Authorization Header 解析 token → 写入 UserContextHolder。
 * <p>
 * 路径白名单（登录、静态资源）由 WebMvcConfig 中 excludePathPatterns 控制。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            throw new UnauthorizedException("缺少认证 token");
        }
        String token = header.substring(PREFIX.length()).trim();
        TokenService.TokenPayload payload = tokenService.verify(token);
        if (payload == null) {
            throw new UnauthorizedException("token 无效或已过期");
        }
        UserEntity user = userMapper.selectById(payload.userId());
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            throw new UnauthorizedException("用户不存在或已停用");
        }
        UserContextHolder.set(new CurrentUser(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}
