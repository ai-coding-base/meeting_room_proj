package com.company.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 代订授权关系。
 * <p>
 * 详见 docs/schema.sql §3。
 */
@Data
@TableName("delegation_grants")
public class DelegationGrantEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 代订人（如助理） */
    private Long delegateId;

    /** 被代订人（如领导） */
    private Long principalId;

    private LocalDate validFrom;

    private LocalDate validTo;

    private LocalDateTime createdAt;

    @TableLogic
    private LocalDateTime deletedAt;
}
