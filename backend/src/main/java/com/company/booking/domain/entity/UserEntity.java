package com.company.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.booking.domain.enums.Role;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工账号实体。
 * <p>
 * 详见 docs/schema.sql §1。
 */
@Data
@TableName("users")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String displayName;

    private String email;

    private String phone;

    private String wecomUserid;

    private String passwordHash;

    private Long departmentId;

    private Long superiorId;

    private Role role;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private LocalDateTime deletedAt;
}
