package com.company.booking.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应。
 */
@Data
@Builder
public class LoginResponse {

    /** 自签名 token，前端存 localStorage */
    private String token;

    /** 当前用户信息 */
    private UserInfoResponse user;
}
