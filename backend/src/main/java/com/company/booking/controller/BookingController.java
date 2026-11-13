package com.company.booking.controller;

import com.company.booking.common.result.R;
import com.company.booking.dto.request.CreateBookingRequest;
import com.company.booking.dto.response.BookingDetailResponse;
import com.company.booking.security.CurrentUser;
import com.company.booking.security.LoginUser;
import com.company.booking.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预订接口。
 * <p>
 * 详见 docs/api.md §3。
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Validated
public class BookingController {

    private final BookingService bookingService;

    /**
     * 创建预订。
     * <p>
     * 详见 docs/prd.md US-02 / US-03、docs/api.md §3.1。
     *
     * @param requestId 客户端幂等 ID（X-Request-Id Header）
     * @param request   创建请求
     * @param user      当前登录用户（由 AuthInterceptor 注入）
     * @return 预订详情
     */
    @PostMapping
    public R<BookingDetailResponse> create(
            @RequestHeader("X-Request-Id") @NotBlank(message = "X-Request-Id 不能为空") String requestId,
            @Valid @RequestBody CreateBookingRequest request,
            @LoginUser CurrentUser user) {
        return R.ok(bookingService.create(user, requestId, request));
    }
}
