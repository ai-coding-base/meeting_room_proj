package com.company.booking.dto.request;

import com.company.booking.domain.enums.Visibility;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建预订请求。
 * <p>
 * 详见 docs/api.md §3.1。
 */
@Data
public class CreateBookingRequest {

    @NotNull(message = "会议室 ID 不能为空")
    @Min(value = 1, message = "会议室 ID 必须为正数")
    private Long roomId;

    /** 被代订人；为空表示自订 */
    @Min(value = 1, message = "被代订人 ID 必须为正数")
    private Long principalId;

    @NotBlank(message = "会议主题不能为空")
    @Size(max = 255, message = "会议主题最长 255 字")
    private String subject;

    @NotNull(message = "开始时间不能为空")
    @Future(message = "开始时间必须为未来时间")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @Future(message = "结束时间必须为未来时间")
    private LocalDateTime endTime;

    @NotNull(message = "参会人数不能为空")
    @Min(value = 1, message = "参会人数至少 1 人")
    private Integer attendeeCount;

    private List<Long> attendeeIds;

    /** 默认全员可见 */
    private Visibility visibility = Visibility.PUBLIC;
}
