package com.company.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码哈希组件配置。
 * <p>
 * 单独引入 spring-security-crypto，仅使用 BCrypt，不引入 Spring Security 全家桶。
 */
@Configuration
public class PasswordConfig {

    /** BCrypt 强度 10（默认）足够 v1.0 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
