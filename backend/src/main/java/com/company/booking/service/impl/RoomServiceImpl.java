package com.company.booking.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.company.booking.domain.entity.RoomEntity;
import com.company.booking.dto.response.RoomListItem;
import com.company.booking.mapper.RoomMapper;
import com.company.booking.service.RoomService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 会议室查询实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomServiceImpl implements RoomService {

    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final TypeReference<List<String>> EQUIPMENT_TYPE = new TypeReference<>() {
    };

    private final RoomMapper roomMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<RoomListItem> listAvailable() {
        List<RoomEntity> rooms = roomMapper.selectList(
                Wrappers.<RoomEntity>lambdaQuery()
                        .eq(RoomEntity::getStatus, STATUS_AVAILABLE)
                        .orderByAsc(RoomEntity::getArea)
                        .orderByAsc(RoomEntity::getFloor)
                        .orderByAsc(RoomEntity::getId));
        return rooms.stream().map(this::toItem).toList();
    }

    private RoomListItem toItem(RoomEntity room) {
        return RoomListItem.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .floor(room.getFloor())
                .area(room.getArea())
                .equipment(parseEquipment(room.getEquipment()))
                .vip(room.getVip())
                .status(room.getStatus())
                .build();
    }

    /**
     * 解析 equipment JSON 字段（如 "[\"projector\",\"tv\"]"）。
     * 解析失败不阻断列表，返回空列表并打日志。
     */
    private List<String> parseEquipment(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(raw, EQUIPMENT_TYPE);
        } catch (Exception e) {
            log.warn("invalid equipment json: {}, err={}", raw, e.getMessage());
            return Collections.emptyList();
        }
    }
}
