package com.company.booking.security;

import com.company.booking.domain.enums.Role;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 自签名 Token 服务（HMAC-SHA256）。
 * <p>
 * Token 格式：Base64URL("payload.sig")
 * payload = "userId|role|expiresAtEpochMs"
 * sig     = HMAC-SHA256(secret, payload) 的前 32 字节 Base64URL
 * <p>
 * 设计取舍：不引入 jjwt，避免增加依赖；payload 简单字符串拼接，足够 v1.0。
 */
@Component
@Slf4j
public class TokenService {

    private static final long TTL_MS = 8 * 60 * 60 * 1000L; // 8 小时
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String SEPARATOR = "|";

    @Value("${app.auth.secret:default-dev-secret-CHANGE-ME-in-prod}")
    private String secret;

    private byte[] secretBytes;

    @PostConstruct
    void init() {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secret.startsWith("default-dev-secret")) {
            log.warn("Using default token secret; please configure app.auth.secret in production");
        }
    }

    /**
     * 签发 token。
     */
    public String issue(Long userId, Role role) {
        long expiresAt = System.currentTimeMillis() + TTL_MS;
        String payload = userId + SEPARATOR + role.getValue() + SEPARATOR + expiresAt;
        String sig = sign(payload);
        return base64Url(payload + "." + sig);
    }

    /**
     * 校验 token。
     *
     * @return [userId, role] 数组；非法/过期返回 null
     */
    public TokenPayload verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int dot = decoded.lastIndexOf('.');
            if (dot < 0) {
                return null;
            }
            String payload = decoded.substring(0, dot);
            String sig = decoded.substring(dot + 1);

            // 常量时间比对，防时序攻击
            if (!MessageDigest.isEqual(sig.getBytes(StandardCharsets.UTF_8),
                    sign(payload).getBytes(StandardCharsets.UTF_8))) {
                return null;
            }

            String[] parts = payload.split("\\|");
            if (parts.length != 3) {
                return null;
            }
            long expiresAt = Long.parseLong(parts[2]);
            if (expiresAt < System.currentTimeMillis()) {
                return null;
            }
            return new TokenPayload(Long.parseLong(parts[0]), Role.valueOf(parts[1]), expiresAt);
        } catch (Exception e) {
            log.debug("token verify failed: {}", e.getMessage());
            return null;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGO));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    private static String base64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    public record TokenPayload(Long userId, Role role, long expiresAt) {
    }
}
