package com.company.booking.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.company.booking.domain.entity.IdempotencyKeyEntity;
import com.company.booking.mapper.IdempotencyKeyMapper;
import com.company.booking.service.IdempotencyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 幂等服务实现。
 * <p>
 * 实现思路：
 * <ol>
 *   <li>先尝试 INSERT idempotency_keys（PK = requestId）</li>
 *   <li>insert 成功 → 首次请求，执行 action 并回写响应</li>
 *   <li>insert 失败（DuplicateKey）→ 重放，读取已有记录的 response 反序列化返回</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final long TTL_HOURS = 24;

    private final IdempotencyKeyMapper idempotencyKeyMapper;
    private final ObjectMapper objectMapper;

    @Override
    public <T> T executeOnce(String requestId, Long userId, String endpoint,
                             Class<T> resultType, Supplier<T> action) {

        // 1. 尝试占位
        IdempotencyKeyEntity holder = new IdempotencyKeyEntity();
        holder.setRequestId(requestId);
        holder.setUserId(userId);
        holder.setEndpoint(endpoint);
        holder.setCreatedAt(LocalDateTime.now());
        holder.setExpiresAt(LocalDateTime.now().plusHours(TTL_HOURS));

        try {
            idempotencyKeyMapper.insert(holder);
            // 首次请求
            T result = action.get();
            persistResponse(requestId, result);
            return result;
        } catch (DuplicateKeyException e) {
            // 重放
            log.info("idempotent replay: requestId={}, userId={}", requestId, userId);
            return loadResponse(requestId, resultType);
        }
    }

    private <T> void persistResponse(String requestId, T result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            IdempotencyKeyEntity update = new IdempotencyKeyEntity();
            update.setRequestId(requestId);
            update.setResponse(json);
            idempotencyKeyMapper.updateById(update);
        } catch (JsonProcessingException e) {
            log.error("serialize idempotency response failed: requestId={}", requestId, e);
        }
    }

    private <T> T loadResponse(String requestId, Class<T> resultType) {
        IdempotencyKeyEntity existing = idempotencyKeyMapper.selectOne(
                Wrappers.<IdempotencyKeyEntity>lambdaQuery()
                        .eq(IdempotencyKeyEntity::getRequestId, requestId));
        if (existing == null || existing.getResponse() == null) {
            throw new IllegalStateException("idempotency record missing for requestId=" + requestId);
        }
        try {
            return objectMapper.readValue(existing.getResponse(), resultType);
        } catch (JsonProcessingException e) {
            log.error("deserialize idempotency response failed", e);
            throw new IllegalStateException("idempotency response corrupted", e);
        }
    }
}
