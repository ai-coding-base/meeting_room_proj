package com.company.booking.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 会议室列表项。
 * <p>
 * 详见 docs/api.md §4（v1.0 新增）。
 */
@Data
@Builder
public class RoomListItem {

    private Long id;

    /** 房间号/房间名（如 "小会议室-A"） */
    private String name;

    /** 容量 */
    private Integer capacity;

    /** 楼层（如 "F3"） */
    private String floor;

    /** 区域/楼栋（如 "北区"、"中庭"） */
    private String area;

    /** 设备清单：["projector","video_conf","whiteboard","tv"] */
    private List<String> equipment;

    /** VIP 会议室强制走审批 */
    private Boolean vip;

    /** AVAILABLE / MAINTENANCE / DECOMMISSIONED */
    private String status;
}
