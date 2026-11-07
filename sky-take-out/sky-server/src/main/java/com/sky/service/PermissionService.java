package com.sky.service;

import com.sky.mapper.PermissionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限查询服务（RBAC）
 *
 * <p>负责查询员工拥有的权限编码集合，供 {@code JwtTokenAdminInterceptor} 做接口级权限校验。</p>
 *
 * <p><b>性能优化</b>：权限数据是典型的「读多写少」，且每次请求都要查询，直接查库会拖慢鉴权。
 * 因此用 Spring Cache（Redis）缓存权限集合，并配合 {@code @CacheEvict}（角色/权限变更时）失效缓存。
 * 缓存的 key 为员工 ID，保证并发安全与正确性。</p>
 */
@Service
public class PermissionService {

    @Autowired
    private PermissionMapper permissionMapper;

    /**
     * 查询员工拥有的权限编码集合（带缓存）
     *
     * @param employeeId 员工 ID
     * @return 权限编码集合（去重）
     */
    @Cacheable(cacheNames = "permCodes", key = "#employeeId")
    public Set<String> listPermCodes(Long employeeId) {
        List<String> codes = permissionMapper.listPermCodesByEmployeeId(employeeId);
        if (codes == null || codes.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(codes);
    }
}
