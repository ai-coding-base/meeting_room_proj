package com.company.booking.dto.response;

import com.company.booking.domain.enums.BookingStatus;
import com.company.booking.domain.enums.Visibility;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预订详情响应。
 * <p>
 * 详见 docs/api.md §3.1。
 */
@Data
@Builder
public class BookingDetailResponse {

    private Long id;
    private Long roomId;
    private String roomName;
    private Long bookerId;
    private Long principalId;
    private String subject;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer attendeeCount;
    private Visibility visibility;
    private BookingStatus status;
    private Boolean needApproval;
    private LocalDateTime createdAt;
}
