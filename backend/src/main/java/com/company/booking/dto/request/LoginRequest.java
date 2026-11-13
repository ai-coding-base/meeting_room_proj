package com.company.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名最长 64 字")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 128, message = "密码长度非法")
    private String password;
}
