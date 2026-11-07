package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色实体（RBAC）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 角色名称 */
    private String roleName;

    /** 角色编码（唯一，如 ADMIN / MANAGER / OPERATOR） */
    private String roleCode;

    /** 角色描述 */
    private String description;

    /** 状态 0:禁用 1:启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
