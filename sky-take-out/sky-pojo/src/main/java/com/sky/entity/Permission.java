package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限实体（RBAC）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 权限名称 */
    private String permName;

    /** 权限编码（唯一，如 dish:add），与 @RequirePermission 注解值对应 */
    private String permCode;

    /** 权限类型 MENU菜单/BUTTON按钮/API接口 */
    private String permType;

    /** 父权限ID（树形结构，0为根） */
    private Long parentId;

    /** 状态 0:禁用 1:启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
