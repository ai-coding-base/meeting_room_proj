package com.company.booking.service;

import com.company.booking.dto.request.CreateBookingRequest;
import com.company.booking.dto.response.BookingDetailResponse;
import com.company.booking.security.CurrentUser;

/**
 * 预订业务服务。
 * <p>
 * 详见 docs/prd.md §3 核心用户故事。
 */
public interface BookingService {

    /**
     * 创建预订。
     * <p>
     * 业务行为：
     * <ul>
     *   <li>幂等：相同 requestId 重放返回首次结果</li>
     *   <li>防超卖：DB 唯一约束 + Service 层异常转换</li>
     *   <li>审批分流：capacity &gt; 50 或 VIP 会议室自动 PENDING</li>
     * </ul>
     * <p>
     * 详见 docs/api.md §3.1。
     *
     * @param user      当前操作人
     * @param requestId 客户端幂等 ID
     * @param request   创建请求
     * @return 预订详情
     */
    BookingDetailResponse create(CurrentUser user, String requestId, CreateBookingRequest request);
}
