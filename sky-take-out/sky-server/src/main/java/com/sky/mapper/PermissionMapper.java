package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限 Mapper（RBAC）
 *
 * <p>核心查询：根据员工 ID 查出其所有角色对应的权限编码集合。
 * 一次 JOIN 完成「员工 → 角色 → 权限」三级关联，供权限拦截器校验使用。
 */
@Mapper
public interface PermissionMapper {

    /**
     * 查询员工拥有的所有权限编码（含角色/权限启用状态过滤）
     *
     * @param employeeId 员工 ID
     * @return 权限编码列表（如 ["dish:add", "order:confirm"]）
     */
    @Select("SELECT DISTINCT p.perm_code " +
            "FROM employee_role er " +
            "JOIN role_permission rp ON er.role_id = rp.role_id " +
            "JOIN permission p ON rp.permission_id = p.id " +
            "JOIN role r ON r.id = er.role_id AND r.status = 1 " +
            "WHERE er.employee_id = #{employeeId} AND p.status = 1")
    List<String> listPermCodesByEmployeeId(@Param("employeeId") Long employeeId);
}
