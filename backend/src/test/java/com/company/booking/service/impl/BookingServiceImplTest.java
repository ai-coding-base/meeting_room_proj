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
import com.company.booking.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BookingServiceImpl 单元测试。
 * <p>
 * 关注点：业务规则（时间合法性、容量、角色、代订授权、审批分流、冲突），
 * 不关心事务/幂等的真实实现（用 IdempotencyService Mock 直接执行 Supplier）。
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private TimeSlotMapper timeSlotMapper;

    @Mock
    private DelegationGrantMapper delegationGrantMapper;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private BookingServiceImpl bookingService;

    /** 默认会议室：8 人，普通（非 VIP），AVAILABLE，capacity 不超 50 */
    private static final long ROOM_ID = 100L;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        // 让 idempotencyService.executeOnce 直接执行 Supplier
        lenient().when(idempotencyService.executeOnce(
                any(), anyLong(), any(), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(4)).get());
    }

    // ---------- 时间合法性 ----------

    @Nested
    @DisplayName("时间合法性校验")
    class TimeValidation {

        @Test
        @DisplayName("过去时间 → InvalidTimeException")
        void pastTime() {
            CurrentUser user = employeeUser();
            LocalDateTime past = nextHalfHour().minusDays(1);
            CreateBookingRequest req = buildReq(ROOM_ID, past, past.plusHours(1));

            assertThatThrownBy(() -> bookingService.create(user, "rid", req))
                    .isInstanceOf(InvalidTimeException.class)
                    .hasMessageContaining("不能预订过去");
        }

        @Test
        @DisplayName("超过 7 天 → InvalidTimeException")
        void tooFarFuture() {
            CurrentUser user = employeeUser();
            LocalDateTime tooFar = nextHalfHour().plusDays(8);
            CreateBookingRequest req = buildReq(ROOM_ID, tooFar, tooFar.plusHours(1));

            assertThatThrownBy(() -> bookingService.create(user, "rid", req))
                    .isInstanceOf(InvalidTimeException.class)
                    .hasMessageContaining("最多提前");
        }

        @Test
        @DisplayName("结束 ≤ 开始 → InvalidTimeException")
        void endBeforeStart() {
            CurrentUser user = employeeUser();
            LocalDateTime start = nextHalfHour().plusHours(2);
            CreateBookingRequest req = buildReq(ROOM_ID, start, start);

            assertThatThrownBy(() -> bookingService.create(user, "rid", req))
                    .isInstanceOf(InvalidTimeException.class)
                    .hasMessageContaining("结束时间必须晚于");
        }

        @Test
        @DisplayName("时长 < 15 分钟 → InvalidTimeException")
        void tooShort() {
            CurrentUser user = employeeUser();
            // 必须用对齐 30 分钟的时间避开前一个分支
            LocalDateTime start = nextHalfHour().plusHours(2);
            // 比 start 多 10 分钟（< 15 分钟）
            CreateBookingRequest req = buildReq(ROOM_ID, start, start.plusMinutes(10));

            assertThatThrownBy(() -> bookingService.create(user, "rid", req))
                    .isInstanceOf(InvalidTimeException.class)
                    .hasMessageContaining("时长不能短于");
        }

        @Test
        @DisplayName("未对齐到 30 分钟 → InvalidTimeException")
        void notAligned() {
            CurrentUser user = employeeUser();
            // 比下一个半点多 5 分钟
            LocalDateTime start = nextHalfHour().plusHours(2).plusMinutes(5);
            CreateBookingRequest req = buildReq(ROOM_ID, start, start.plusHours(1));

            assertThatThrownBy(() -> bookingService.create(user, "rid", req))
                    .isInstanceOf(InvalidTimeException.class)
                    .hasMessageContaining("对齐");
        }
    }

    // ---------- 会议室校验 ----------

    @Nested
    @DisplayName("会议室校验")
    class RoomValidation {

        @Test
        @DisplayName("会议室不存在 → ROOM_NOT_FOUND")
        void roomNotFound() {
            when(roomMapper.selectById(ROOM_ID)).thenReturn(null);

            CurrentUser user = employeeUser();
            CreateBookingRequest req = validReq();

            assertThatThrownBy(() -> bookingService.create(user, "rid", req))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo("ROOM_NOT_FOUND");
        }

        @Test
        @DisplayName("会议室不可用 (MAINTENANCE) → ROOM_NOT_AVAILABLE")
        void roomUnavailable() {
            RoomEntity room = newRoom(8, false);
            room.setStatus("MAINTENANCE");
            when(roomMapper.selectById(ROOM_ID)).thenReturn(room);

            assertThatThrownBy(() -> bookingService.create(employeeUser(), "rid", validReq()))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo("ROOM_NOT_AVAILABLE");
        }

        @Test
        @DisplayName("人数超出容量 → CAPACITY_EXCEEDED")
        void capacityExceeded() {
            RoomEntity room = newRoom(4, false);
            when(roomMapper.selectById(ROOM_ID)).thenReturn(room);

            CreateBookingRequest req = validReq();
            req.setAttendeeCount(10);

            assertThatThrownBy(() -> bookingService.create(employeeUser(), "rid", req))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo("CAPACITY_EXCEEDED");
        }
    }

    // ---------- 代订授权 ----------

    @Nested
    @DisplayName("代订授权校验")
    class DelegationValidation {

        @Test
        @DisplayName("EMPLOYEE 代订他人 → ForbiddenException")
        void employeeCannotDelegate() {
            CurrentUser user = employeeUser(); // id=1
            CreateBookingRequest req = validReq();
            req.setPrincipalId(999L); // 非自己

            assertThatThrownBy(() -> bookingService.create(user, "rid", req))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("当前角色无权代订");
        }

        @Test
        @DisplayName("ASSISTANT 代订未授权用户 → ForbiddenException")
        void assistantWithoutGrant() {
            CurrentUser user = new CurrentUser(2L, "carol", "Carol", Role.ASSISTANT);
            CreateBookingRequest req = validReq();
            req.setPrincipalId(999L);

            when(delegationGrantMapper.existsValid(eq(2L), eq(999L), any(LocalDate.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> bookingService.create(user, "rid", req))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("未获得");
        }

        @Test
        @DisplayName("ASSISTANT 代订已授权用户 → 创建成功")
        void assistantWithGrant_success() {
            CurrentUser user = new CurrentUser(2L, "carol", "Carol", Role.ASSISTANT);
            when(delegationGrantMapper.existsValid(eq(2L), eq(999L), any(LocalDate.class)))
                    .thenReturn(true);
            when(roomMapper.selectById(ROOM_ID)).thenReturn(newRoom(8, false));

            CreateBookingRequest req = validReq();
            req.setPrincipalId(999L);

            BookingDetailResponse res = bookingService.create(user, "rid", req);

            assertThat(res).isNotNull();
            assertThat(res.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            // principalId 透传到实体
            ArgumentCaptor<BookingEntity> captor = ArgumentCaptor.forClass(BookingEntity.class);
            verify(bookingMapper).insert(captor.capture());
            assertThat(captor.getValue().getPrincipalId()).isEqualTo(999L);
            assertThat(captor.getValue().getBookerId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("ADMIN 代订任意人 → 直接放行（不查授权表）")
        void adminBypassDelegation() {
            CurrentUser user = new CurrentUser(99L, "admin", "Admin", Role.ADMIN);
            when(roomMapper.selectById(ROOM_ID)).thenReturn(newRoom(8, false));

            CreateBookingRequest req = validReq();
            req.setPrincipalId(999L);

            BookingDetailResponse res = bookingService.create(user, "rid", req);

            assertThat(res.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            verify(delegationGrantMapper, never())
                    .existsValid(anyLong(), anyLong(), any(LocalDate.class));
        }

        @Test
        @DisplayName("principalId 等于自己 → 视为自订，不查授权")
        void selfPrincipal() {
            CurrentUser user = employeeUser();
            when(roomMapper.selectById(ROOM_ID)).thenReturn(newRoom(8, false));

            CreateBookingRequest req = validReq();
            req.setPrincipalId(USER_ID); // 等于自己

            BookingDetailResponse res = bookingService.create(user, "rid", req);

            assertThat(res.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            verify(delegationGrantMapper, never())
                    .existsValid(anyLong(), anyLong(), any(LocalDate.class));
        }
    }

    // ---------- 审批分流 ----------

    @Nested
    @DisplayName("审批分流")
    class ApprovalRouting {

        @Test
        @DisplayName("普通会议室（capacity ≤ 50, 非 VIP） → CONFIRMED")
        void normalRoom_confirmed() {
            when(roomMapper.selectById(ROOM_ID)).thenReturn(newRoom(8, false));

            BookingDetailResponse res = bookingService.create(employeeUser(), "rid", validReq());

            assertThat(res.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(res.getNeedApproval()).isFalse();
        }

        @Test
        @DisplayName("大型会议室（capacity > 50） → PENDING + needApproval")
        void largeRoom_pending() {
            when(roomMapper.selectById(ROOM_ID)).thenReturn(newRoom(60, false));

            CreateBookingRequest req = validReq();
            req.setAttendeeCount(40);
            BookingDetailResponse res = bookingService.create(employeeUser(), "rid", req);

            assertThat(res.getStatus()).isEqualTo(BookingStatus.PENDING);
            assertThat(res.getNeedApproval()).isTrue();
        }

        @Test
        @DisplayName("VIP 会议室（即便 capacity 小） → PENDING")
        void vipRoom_pending() {
            when(roomMapper.selectById(ROOM_ID)).thenReturn(newRoom(10, true));

            BookingDetailResponse res = bookingService.create(employeeUser(), "rid", validReq());

            assertThat(res.getStatus()).isEqualTo(BookingStatus.PENDING);
            assertThat(res.getNeedApproval()).isTrue();
        }
    }

    // ---------- 槽位与冲突 ----------

    @Nested
    @DisplayName("时间槽切分与冲突")
    class SlotAndConflict {

        @Test
        @DisplayName("1 小时预订 → 切分为 2 个 30 分钟槽")
        void oneHour_twoSlots() {
            when(roomMapper.selectById(ROOM_ID)).thenReturn(newRoom(8, false));

            bookingService.create(employeeUser(), "rid", validReq());

            ArgumentCaptor<List<TimeSlotEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(timeSlotMapper).insertBatch(captor.capture());
            List<TimeSlotEntity> slots = captor.getValue();
            assertThat(slots).hasSize(2);
            assertThat(slots.get(0).getSlotEnd()).isEqualTo(slots.get(1).getSlotStart());
        }

        @Test
        @DisplayName("DB 唯一约束冲突 → BookingConflictException")
        void duplicateKey_throwsConflict() {
            when(roomMapper.selectById(ROOM_ID)).thenReturn(newRoom(8, false));
            when(timeSlotMapper.insertBatch(any()))
                    .thenThrow(new DuplicateKeyException("uk_room_slot"));

            assertThatThrownBy(() -> bookingService.create(employeeUser(), "rid", validReq()))
                    .isInstanceOf(BookingConflictException.class)
                    .hasMessageContaining("已被预订");
        }
    }

    // ---------- helpers ----------

    private CurrentUser employeeUser() {
        return new CurrentUser(USER_ID, "alice", "Alice", Role.EMPLOYEE);
    }

    private RoomEntity newRoom(int capacity, boolean vip) {
        RoomEntity r = new RoomEntity();
        r.setId(ROOM_ID);
        r.setName("R-" + ROOM_ID);
        r.setCapacity(capacity);
        r.setVip(vip);
        r.setStatus("AVAILABLE");
        return r;
    }

    /**
     * 默认有效请求：明天的下一个半点 + 1 小时，5 人，PUBLIC。
     */
    private CreateBookingRequest validReq() {
        LocalDateTime start = nextHalfHour().plusDays(1);
        return buildReq(ROOM_ID, start, start.plusHours(1));
    }

    private CreateBookingRequest buildReq(long roomId, LocalDateTime start, LocalDateTime end) {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setRoomId(roomId);
        req.setSubject("单测会议");
        req.setStartTime(start);
        req.setEndTime(end);
        req.setAttendeeCount(5);
        req.setVisibility(Visibility.PUBLIC);
        return req;
    }

    /** 返回当前时间下一个对齐到 30 分钟整、second/nano 都为 0 的时间点。 */
    private static LocalDateTime nextHalfHour() {
        LocalDateTime now = LocalDateTime.now()
                .withSecond(0)
                .withNano(0);
        int min = now.getMinute();
        int add = (min < 30) ? (30 - min) : (60 - min);
        return now.plusMinutes(add);
    }
}
