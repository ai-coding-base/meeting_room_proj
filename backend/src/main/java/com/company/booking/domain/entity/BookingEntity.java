package com.company.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.booking.domain.enums.BookingStatus;
import com.company.booking.domain.enums.Visibility;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预订实体。
 * <p>
 * 详见 docs/schema.sql §6。
 */
@Data
@TableName(value = "bookings", autoResultMap = true)
public class BookingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    /** 实际操作人 */
    private Long bookerId;

    /** 被代订人；非代订时 = bookerId */
    private Long principalId;

    private String subject;

    /** 参会人 ID 列表的 JSON 字符串（v1.0 简化处理） */
    private String attendees;

    private Integer attendeeCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Visibility visibility;

    private BookingStatus status;

    private Long approverId;

    private LocalDateTime approvedAt;

    private String rejectReason;

    private Long cancelledBy;

    private LocalDateTime cancelledAt;

    private String cancelReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private LocalDateTime deletedAt;
}
