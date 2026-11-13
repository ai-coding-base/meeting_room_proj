package com.company.booking.service;

import com.company.booking.dto.response.RoomListItem;

import java.util.List;

/**
 * 会议室查询服务。
 */
public interface RoomService {

    /**
     * 列出所有可用（status=AVAILABLE）会议室。
     * v1.0 数据量小，无分页。
     */
    List<RoomListItem> listAvailable();
}
