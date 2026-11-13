package com.company.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议室实体。
 * <p>
 * 详见 docs/schema.sql §4。
 */
@Data
@TableName("rooms")
public class RoomEntity {

    @TableId
    private Long id;

    private String name;

    private Integer capacity;

    private String floor;

    private String area;

    /** JSON 字符串：["projector","video_conf"] */
    private String equipment;

    @TableField("is_vip")
    private Boolean vip;

    private Long departmentId;

    /** AVAILABLE / MAINTENANCE / DECOMMISSIONED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private LocalDateTime deletedAt;
}
