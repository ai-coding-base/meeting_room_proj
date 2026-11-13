package com.company.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.company.booking.domain.entity.RoomEntity;
import com.company.booking.dto.response.RoomListItem;
import com.company.booking.mapper.RoomMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RoomServiceImpl 单元测试。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>正常 JSON equipment 解析</li>
 *   <li>equipment 为 null / 空串 / 非法 JSON 时返回空列表（不抛异常）</li>
 *   <li>多条记录字段映射正确</li>
 *   <li>空查询结果返回空列表</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    @Mock
    private RoomMapper roomMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RoomServiceImpl roomService;

    @Test
    @DisplayName("equipment 是合法 JSON 数组 → 正确解析为 List<String>")
    void listAvailable_validEquipmentJson() {
        RoomEntity r = newRoom(1L, "小会议室-A", 8, "F2", "A 栋",
                "[\"projector\",\"whiteboard\"]", false);
        when(roomMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r));

        List<RoomListItem> list = roomService.listAvailable();

        assertThat(list).hasSize(1);
        RoomListItem item = list.get(0);
        assertThat(item.getId()).isEqualTo(1L);
        assertThat(item.getName()).isEqualTo("小会议室-A");
        assertThat(item.getCapacity()).isEqualTo(8);
        assertThat(item.getFloor()).isEqualTo("F2");
        assertThat(item.getArea()).isEqualTo("A 栋");
        assertThat(item.getEquipment()).containsExactly("projector", "whiteboard");
        assertThat(item.getVip()).isFalse();
        assertThat(item.getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("equipment 为 null → 返回空列表")
    void listAvailable_nullEquipment() {
        RoomEntity r = newRoom(1L, "X", 4, "F1", "B", null, false);
        when(roomMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r));

        List<RoomListItem> list = roomService.listAvailable();

        assertThat(list.get(0).getEquipment()).isEmpty();
    }

    @Test
    @DisplayName("equipment 为空字符串 / 空白 → 返回空列表")
    void listAvailable_blankEquipment() {
        RoomEntity r1 = newRoom(1L, "X1", 4, "F1", "B", "", false);
        RoomEntity r2 = newRoom(2L, "X2", 4, "F1", "B", "   ", false);
        when(roomMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r1, r2));

        List<RoomListItem> list = roomService.listAvailable();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getEquipment()).isEmpty();
        assertThat(list.get(1).getEquipment()).isEmpty();
    }

    @Test
    @DisplayName("equipment 是非法 JSON → 不抛异常，返回空列表")
    void listAvailable_invalidJson_returnsEmpty() {
        RoomEntity r = newRoom(1L, "X", 4, "F1", "B", "{not-json", false);
        when(roomMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r));

        List<RoomListItem> list = roomService.listAvailable();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getEquipment()).isEmpty();
    }

    @Test
    @DisplayName("DB 返回空列表 → 业务返回空列表")
    void listAvailable_emptyResult() {
        when(roomMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThat(roomService.listAvailable()).isEmpty();
    }

    @Test
    @DisplayName("多条记录都被正确转换")
    void listAvailable_multipleRows() {
        RoomEntity r1 = newRoom(1L, "A1", 8, "F2", "A 栋", "[\"projector\"]", false);
        RoomEntity r2 = newRoom(2L, "VIP", 20, "F8", "VIP 楼层",
                "[\"projector\",\"tv\",\"video_conf\"]", true);
        when(roomMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r1, r2));

        List<RoomListItem> list = roomService.listAvailable();

        assertThat(list).hasSize(2);
        assertThat(list.get(1).getVip()).isTrue();
        assertThat(list.get(1).getEquipment()).containsExactly("projector", "tv", "video_conf");
    }

    private RoomEntity newRoom(Long id, String name, int cap, String floor, String area,
                               String equipment, boolean vip) {
        RoomEntity e = new RoomEntity();
        e.setId(id);
        e.setName(name);
        e.setCapacity(cap);
        e.setFloor(floor);
        e.setArea(area);
        e.setEquipment(equipment);
        e.setVip(vip);
        e.setStatus("AVAILABLE");
        return e;
    }
}
