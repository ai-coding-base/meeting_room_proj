package com.company.booking.security;

import com.company.booking.domain.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenService 单元测试：
 * <ul>
 *     <li>签发 + 校验往返</li>
 *     <li>非法 token / 篡改 / 过期 / null / 空</li>
 *     <li>错误的 secret 应使签名失效</li>
 * </ul>
 */
class TokenServiceTest {

    private static final String SECRET = "unit-test-secret-1234567890";

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        // @Value 字段在单测里没有 Spring 容器，手动注入
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
        // 触发 @PostConstruct 等价初始化
        ReflectionTestUtils.invokeMethod(tokenService, "init");
    }

    @Test
    @DisplayName("issue + verify 往返：合法 token 能正确解析出 userId 和 role")
    void issue_then_verify_returnsSamePayload() {
        String token = tokenService.issue(42L, Role.ADMIN);

        TokenService.TokenPayload payload = tokenService.verify(token);

        assertThat(payload).isNotNull();
        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.role()).isEqualTo(Role.ADMIN);
        assertThat(payload.expiresAt()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    @DisplayName("verify(null) 返回 null")
    void verify_null_returnsNull() {
        assertThat(tokenService.verify(null)).isNull();
    }

    @Test
    @DisplayName("verify(空串) 返回 null")
    void verify_blank_returnsNull() {
        assertThat(tokenService.verify("")).isNull();
        assertThat(tokenService.verify("   ")).isNull();
    }

    @Test
    @DisplayName("verify(垃圾字符串) 返回 null")
    void verify_garbage_returnsNull() {
        assertThat(tokenService.verify("not-a-valid-token")).isNull();
    }

    @Test
    @DisplayName("verify(无分隔符的 base64 内容) 返回 null")
    void verify_noDot_returnsNull() {
        // payload 内不含 '.' → lastIndexOf('.') == -1
        String fake = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("1|ADMIN|9999999999999".getBytes(StandardCharsets.UTF_8));
        assertThat(tokenService.verify(fake)).isNull();
    }

    @Test
    @DisplayName("verify(签名被篡改) 返回 null")
    void verify_tamperedSig_returnsNull() {
        String token = tokenService.issue(1L, Role.EMPLOYEE);

        // 解码 → 改最后 1 个字符 → 重新编码
        String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        char last = decoded.charAt(decoded.length() - 1);
        char tampered = last == 'A' ? 'B' : 'A';
        String tamperedDecoded = decoded.substring(0, decoded.length() - 1) + tampered;
        String tamperedToken = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedDecoded.getBytes(StandardCharsets.UTF_8));

        assertThat(tokenService.verify(tamperedToken)).isNull();
    }

    @Test
    @DisplayName("verify(payload 被篡改但签名未变) 返回 null")
    void verify_tamperedPayload_returnsNull() throws Exception {
        // 用反射调用私有 sign 方法构造一个签名不匹配 payload 的 token
        String fakePayload = "999|ADMIN|" + (System.currentTimeMillis() + 60_000);
        Method sign = TokenService.class.getDeclaredMethod("sign", String.class);
        sign.setAccessible(true);
        // 用一个不一样的 payload 算签名
        String wrongSig = (String) sign.invoke(tokenService, "1|EMPLOYEE|0");
        String forged = fakePayload + "." + wrongSig;
        String fakeToken = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(forged.getBytes(StandardCharsets.UTF_8));

        assertThat(tokenService.verify(fakeToken)).isNull();
    }

    @Test
    @DisplayName("verify(过期 token) 返回 null")
    void verify_expired_returnsNull() throws Exception {
        // 直接构造过期 payload
        String expiredPayload = "1|EMPLOYEE|" + (System.currentTimeMillis() - 1_000);
        Method sign = TokenService.class.getDeclaredMethod("sign", String.class);
        sign.setAccessible(true);
        String sig = (String) sign.invoke(tokenService, expiredPayload);
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((expiredPayload + "." + sig).getBytes(StandardCharsets.UTF_8));

        assertThat(tokenService.verify(token)).isNull();
    }

    @Test
    @DisplayName("verify(payload 字段数不对) 返回 null")
    void verify_wrongPayloadFields_returnsNull() throws Exception {
        // 只有两段：1|ADMIN
        String shortPayload = "1|ADMIN";
        Method sign = TokenService.class.getDeclaredMethod("sign", String.class);
        sign.setAccessible(true);
        String sig = (String) sign.invoke(tokenService, shortPayload);
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((shortPayload + "." + sig).getBytes(StandardCharsets.UTF_8));

        assertThat(tokenService.verify(token)).isNull();
    }

    @Test
    @DisplayName("不同 secret 的 TokenService 互不认可对方签发的 token")
    void differentSecret_cannotVerifyEachOther() {
        TokenService other = new TokenService();
        ReflectionTestUtils.setField(other, "secret", "completely-different-secret-key-xyz");
        ReflectionTestUtils.invokeMethod(other, "init");

        String tokenFromOther = other.issue(7L, Role.MANAGER);

        // 用本 service 校验 other 签发的 token → null
        assertThat(tokenService.verify(tokenFromOther)).isNull();
        // 但 other 自己能验通过
        assertThat(other.verify(tokenFromOther)).isNotNull();
    }

    @Test
    @DisplayName("issue 对所有角色都能签发并验证")
    void issue_allRoles() {
        for (Role role : Role.values()) {
            String token = tokenService.issue(100L, role);
            TokenService.TokenPayload payload = tokenService.verify(token);
            assertThat(payload).as("role=%s", role).isNotNull();
            assertThat(payload.role()).isEqualTo(role);
        }
    }
}
