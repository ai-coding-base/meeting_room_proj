package com.company.booking.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 用户角色。
 * <p>
 * 详见 docs/prd.md §2 用户角色。
 */
public enum Role {

    /** 普通员工：仅能预订/取消自己的会议 */
    EMPLOYEE("EMPLOYEE", "员工"),

    /** 助理：可代订被授权领导的会议 */
    ASSISTANT("ASSISTANT", "助理"),

    /** 部门主管 */
    MANAGER("MANAGER", "部门主管"),

    /** VIP/大型会议室审批人 */
    APPROVER("APPROVER", "审批人"),

    /** 系统管理员：全权限 */
    ADMIN("ADMIN", "管理员"),

    /** 前台接待 */
    RECEPTIONIST("RECEPTIONIST", "前台"),

    /** 审计：只读全员预订 */
    AUDITOR("AUDITOR", "审计");

    @EnumValue
    private final String value;

    private final String label;

    Role(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }
}
