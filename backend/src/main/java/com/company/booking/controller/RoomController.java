package com.company.booking.controller;

import com.company.booking.common.result.R;
import com.company.booking.dto.response.RoomListItem;
import com.company.booking.security.CurrentUser;
import com.company.booking.security.LoginUser;
import com.company.booking.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会议室接口。
 * <p>
 * 详见 docs/api.md §4。
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    /**
     * 列出全部可用会议室（无分页，v1.0 数据量小）。
     *
     * @param user 当前登录用户（保留入参以便后续按部门权限过滤；当前未使用）
     */
    @GetMapping
    public R<List<RoomListItem>> list(@LoginUser CurrentUser user) {
        return R.ok(roomService.listAvailable());
    }
}
