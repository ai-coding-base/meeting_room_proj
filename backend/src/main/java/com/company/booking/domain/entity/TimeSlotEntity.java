package com.company.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 时间槽占用实体。
 * 一次预订时长跨多个 30min 槽位时，会写入多行（同一 bookingId）。
 * <p>
 * 唯一约束 uk_room_slot(room_id, slot_start) 是防超卖的核心机制。
 * <p>
 * 详见 docs/schema.md §2.1。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("time_slots")
public class TimeSlotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private LocalDateTime slotStart;

    private LocalDateTime slotEnd;

    private Long bookingId;

    private LocalDateTime createdAt;
}
