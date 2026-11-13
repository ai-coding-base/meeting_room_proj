package com.company.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等键实体。
 * 用于防止客户端因网络重试导致的重复提交。
 * <p>
 * 详见 docs/schema.md §2.3。
 */
@Data
@TableName("idempotency_keys")
public class IdempotencyKeyEntity {

    @TableId
    private String requestId;

    private Long userId;

    private String endpoint;

    private Long bookingId;

    /** 完整响应 JSON 快照 */
    private String response;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;
}
