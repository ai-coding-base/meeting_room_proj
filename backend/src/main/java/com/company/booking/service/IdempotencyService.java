package com.company.booking.service;

import java.util.function.Supplier;

/**
 * 幂等执行服务。
 * <p>
 * 同一 (requestId, userId, endpoint) 的重复请求会返回首次执行结果，
 * 不会触发 action 第二次执行。
 * <p>
 * 详见 docs/schema.md §2.3。
 */
public interface IdempotencyService {

    /**
     * 在幂等保护下执行业务动作。
     *
     * @param requestId   客户端 UUID（X-Request-Id Header）
     * @param userId      当前用户 ID
     * @param endpoint    接口标识，如 "POST /api/bookings"
     * @param resultType  返回值类型（用于重放时反序列化）
     * @param action      实际业务动作
     * @param <T>         返回类型
     * @return 首次或重放的执行结果
     */
    <T> T executeOnce(String requestId, Long userId, String endpoint, Class<T> resultType, Supplier<T> action);
}
