package com.company.booking.service.impl;

import com.company.booking.common.exception.BizException;
import com.company.booking.common.exception.BookingConflictException;
import com.company.booking.common.exception.ForbiddenException;
import com.company.booking.common.exception.InvalidTimeException;
import com.company.booking.domain.entity.BookingEntity;
import com.company.booking.domain.entity.RoomEntity;
import com.company.booking.domain.entity.TimeSlotEntity;
import com.company.booking.domain.enums.BookingStatus;
import com.company.booking.domain.enums.Role;
import com.company.booking.domain.enums.Visibility;
import com.company.booking.dto.request.CreateBookingRequest;
import com.company.booking.dto.response.BookingDetailResponse;
import com.company.booking.mapper.BookingMapper;
import com.company.booking.mapper.DelegationGrantMapper;
import com.company.booking.mapper.RoomMapper;
import com.company.booking.mapper.TimeSlotMapper;
import com.company.booking.security.CurrentUser;
import com.company.booking.service.BookingService;
import com.company.booking.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 预订服务实现。
 * <p>
 * 详见 docs/prd.md US-02 / US-03、docs/schema.md §2.1（防超卖）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private static final int VIP_CAPACITY_THRESHOLD = 50;
    private static final int SLOT_MINUTES = 30;
    private static final int MIN_DURATION_MINUTES = 15;
    private static final int MAX_ADVANCE_DAYS = 7;

    private static final String ENDPOINT = "POST /api/bookings";

    private final RoomMapper roomMapper;
    private final BookingMapper bookingMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final DelegationGrantMapper delegationGrantMapper;
    private final IdempotencyService idempotencyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse create(CurrentUser user, String requestId, CreateBookingRequest req) {
        return idempotencyService.executeOnce(
                requestId, user.id(), ENDPOINT, BookingDetailResponse.class,
                () -> doCreate(user, req));
    }

    /**
     * 实际创建逻辑（在外层事务内执行）。
     * <p>
     * 步骤：
     * <ol>
     *   <li>校验时间合法性</li>
     *   <li>查询并校验会议室（容量、状态）</li>
     *   <li>判定是否需要审批（capacity &gt; 50 或 VIP）</li>
     *   <li>插入 booking + time_slots（依赖唯一约束防超卖）</li>
     * </ol>
     */
    private BookingDetailResponse doCreate(CurrentUser user, CreateBookingRequest req) {
        // 1. 时间校验
        validateTimeRange(req.getStartTime(), req.getEndTime());

        // 2. 代订授权校验（principalId 非空且不是自己时）
        validateDelegation(user, req.getPrincipalId());

        // 3. 会议室校验
        RoomEntity room = loadAndValidateRoom(req.getRoomId(), req.getAttendeeCount());

        // 4. 审批分流
        boolean needApproval = room.getCapacity() > VIP_CAPACITY_THRESHOLD
                || Boolean.TRUE.equals(room.getVip());

        // 5. 插入 booking
        BookingEntity booking = buildBooking(user, req, needApproval);
        bookingMapper.insert(booking);

        // 6. 插入 time_slots（防超卖核心）
        List<TimeSlotEntity> slots = buildSlots(booking);
        try {
            timeSlotMapper.insertBatch(slots);
        } catch (DuplicateKeyException e) {
            log.warn("time slot conflict: room={}, start={}, end={}",
                    booking.getRoomId(), booking.getStartTime(), booking.getEndTime());
            throw new BookingConflictException("该时段已被预订", e);
        }

        log.info("booking created: id={}, room={}, user={}, status={}",
                booking.getId(), booking.getRoomId(), user.id(), booking.getStatus());

        return toResponse(booking, room, needApproval);
    }

    // ---------- 校验 ----------

    /**
     * 校验代订授权。
     * <ul>
     *   <li>principalId 为空或等于自己 → 自订，直接放行</li>
     *   <li>ADMIN → 全权限，直接放行</li>
     *   <li>ASSISTANT → 必须存在有效授权（delegation_grants）</li>
     *   <li>其他角色 → 一律禁止代订</li>
     * </ul>
     */
    private void validateDelegation(CurrentUser user, Long principalId) {
        if (principalId == null || principalId.equals(user.id())) {
            return;
        }
        if (user.isAdmin()) {
            return;
        }
        if (user.role() != Role.ASSISTANT) {
            throw new ForbiddenException("当前角色无权代订他人会议");
        }
        boolean valid = delegationGrantMapper.existsValid(user.id(), principalId, LocalDate.now());
        if (!valid) {
            throw new ForbiddenException("未获得该用户的代订授权");
        }
    }

    /**
     * 校验时间合法性：未来时间、对齐 30 分钟、时长 ≥ 15 分钟、不超过 7 天。
     */
    private void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now();

        if (!start.isAfter(now)) {
            throw new InvalidTimeException("不能预订过去的时间");
        }
        if (start.isAfter(now.plusDays(MAX_ADVANCE_DAYS))) {
            throw new InvalidTimeException("最多提前 " + MAX_ADVANCE_DAYS + " 天预订");
        }
        if (!end.isAfter(start)) {
            throw new InvalidTimeException("结束时间必须晚于开始时间");
        }
        long minutes = ChronoUnit.MINUTES.between(start, end);
        if (minutes < MIN_DURATION_MINUTES) {
            throw new InvalidTimeException("预订时长不能短于 " + MIN_DURATION_MINUTES + " 分钟");
        }
        if (start.getMinute() % SLOT_MINUTES != 0 || start.getSecond() != 0) {
            throw new InvalidTimeException("开始时间必须对齐到 " + SLOT_MINUTES + " 分钟整");
        }
    }

    private RoomEntity loadAndValidateRoom(Long roomId, int attendeeCount) {
        RoomEntity room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BizException("ROOM_NOT_FOUND", "会议室不存在");
        }
        if (!"AVAILABLE".equals(room.getStatus())) {
            throw new BizException("ROOM_NOT_AVAILABLE", "会议室不可用");
        }
        if (attendeeCount > room.getCapacity()) {
            throw new BizException("CAPACITY_EXCEEDED",
                    "参会人数超过会议室容量 " + room.getCapacity());
        }
        return room;
    }

    // ---------- 构造 ----------

    private BookingEntity buildBooking(CurrentUser user, CreateBookingRequest req, boolean needApproval) {
        BookingEntity entity = new BookingEntity();
        entity.setRoomId(req.getRoomId());
        entity.setBookerId(user.id());
        entity.setPrincipalId(req.getPrincipalId() != null ? req.getPrincipalId() : user.id());
        entity.setSubject(req.getSubject());
        entity.setAttendeeCount(req.getAttendeeCount());
        entity.setStartTime(req.getStartTime());
        entity.setEndTime(req.getEndTime());
        entity.setVisibility(req.getVisibility() != null ? req.getVisibility() : Visibility.PUBLIC);
        entity.setStatus(needApproval ? BookingStatus.PENDING : BookingStatus.CONFIRMED);
        return entity;
    }

    /**
     * 将预订时间区间打散为 30 分钟槽位。
     * 例：09:00–10:30 → 3 个槽位
     */
    private List<TimeSlotEntity> buildSlots(BookingEntity booking) {
        List<TimeSlotEntity> result = new ArrayList<>();
        LocalDateTime cursor = booking.getStartTime();
        while (cursor.isBefore(booking.getEndTime())) {
            LocalDateTime slotEnd = cursor.plusMinutes(SLOT_MINUTES);
            result.add(new TimeSlotEntity(null, booking.getRoomId(), cursor, slotEnd,
                    booking.getId(), null));
            cursor = slotEnd;
        }
        return result;
    }

    // ---------- 转换 ----------

    private BookingDetailResponse toResponse(BookingEntity booking, RoomEntity room, boolean needApproval) {
        return BookingDetailResponse.builder()
                .id(booking.getId())
                .roomId(booking.getRoomId())
                .roomName(room.getName())
                .bookerId(booking.getBookerId())
                .principalId(booking.getPrincipalId())
                .subject(booking.getSubject())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .attendeeCount(booking.getAttendeeCount())
                .visibility(booking.getVisibility())
                .status(booking.getStatus())
                .needApproval(needApproval)
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
