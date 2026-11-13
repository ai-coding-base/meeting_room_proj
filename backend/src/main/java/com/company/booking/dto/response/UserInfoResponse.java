package com.company.booking.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 当前登录用户信息（脱敏，无 password_hash）。
 */
@Data
@Builder
public class UserInfoResponse {

    private Long id;

    private String username;

    private String displayName;

    /** 角色 value，如 EMPLOYEE / ASSISTANT / ADMIN */
    private String role;

    /** 角色中文名 */
    private String roleLabel;
}
