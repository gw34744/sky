package com.sky.service.impl;

import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * 员工业务层单元测试
 *
 * <p>覆盖点：
 * <ul>
 *     <li>登录：成功、账号不存在、密码错误、账号锁定</li>
 *     <li>新增员工：默认启用状态 + 默认密码 123456 的 BCrypt 加密</li>
 *     <li>根据 id 查询：密码脱敏为 ****</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    /**
     * 测试专用加密器，用于生成与业务代码一致的 BCrypt 哈希。
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("登录成功：返回员工对象")
    void testLogin_success() {
        // given
        String username = "admin";
        String rawPassword = "123456";
        Employee employee = Employee.builder()
                .id(1L)
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .status(StatusConstant.ENABLE)
                .build();
        when(employeeMapper.getByUsername(username)).thenReturn(employee);

        EmployeeLoginDTO loginDTO = new EmployeeLoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(rawPassword);

        // when
        Employee result = employeeService.login(loginDTO);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("登录失败：账号不存在")
    void testLogin_throwsWhenAccountNotFound() {
        // given
        String username = "ghost";
        when(employeeMapper.getByUsername(username)).thenReturn(null);

        EmployeeLoginDTO loginDTO = new EmployeeLoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword("123456");

        // when & then
        AccountNotFoundException ex = assertThrows(
                AccountNotFoundException.class,
                () -> employeeService.login(loginDTO)
        );
        assertEquals("账号不存在", ex.getMessage());
    }

    @Test
    @DisplayName("登录失败：密码错误")
    void testLogin_throwsWhenPasswordError() {
        // given
        String username = "admin";
        Employee employee = Employee.builder()
                .id(1L)
                .username(username)
                .password(passwordEncoder.encode("correct-password"))
                .status(StatusConstant.ENABLE)
                .build();
        when(employeeMapper.getByUsername(username)).thenReturn(employee);

        EmployeeLoginDTO loginDTO = new EmployeeLoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword("wrong-password");

        // when & then
        PasswordErrorException ex = assertThrows(
                PasswordErrorException.class,
                () -> employeeService.login(loginDTO)
        );
        assertEquals("密码错误", ex.getMessage());
    }

    @Test
    @DisplayName("登录失败：账号被锁定")
    void testLogin_throwsWhenAccountLocked() {
        // given
        String username = "admin";
        String rawPassword = "123456";
        Employee employee = Employee.builder()
                .id(1L)
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .status(StatusConstant.DISABLE)
                .build();
        when(employeeMapper.getByUsername(username)).thenReturn(employee);

        EmployeeLoginDTO loginDTO = new EmployeeLoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(rawPassword);

        // when & then
        AccountLockedException ex = assertThrows(
                AccountLockedException.class,
                () -> employeeService.login(loginDTO)
        );
        assertEquals("账号被锁定", ex.getMessage());
    }

    @Test
    @DisplayName("新增员工：默认启用状态，密码为 123456 的 BCrypt 加密")
    void testSave_setsDefaultStatusAndPassword() {
        // given
        BaseContext.setCurrentId(1L);
        EmployeeDTO dto = new EmployeeDTO();
        dto.setUsername("zhangsan");
        dto.setName("张三");
        dto.setPhone("13800000000");

        // when
        employeeService.save(dto);

        // then：密码应为 BCrypt 格式（$2a$ 开头），且能用 BCrypt 验证回默认密码 123456
        verify(employeeMapper, times(1)).insert(argThat(e ->
                e.getStatus().equals(StatusConstant.ENABLE)
                        && e.getPassword().startsWith("$2a$")
                        && passwordEncoder.matches(PasswordConstant.DEFAULT_PASSWORD, e.getPassword())
                        && e.getCreateUser().equals(1L)
                        && e.getUpdateUser().equals(1L)
        ));
    }

    @Test
    @DisplayName("根据 id 查询员工：密码脱敏为 ****")
    void testGetById_masksPassword() {
        // given
        Long id = 1L;
        Employee employee = Employee.builder()
                .id(id)
                .username("admin")
                .password("some-hashed-password")
                .build();
        when(employeeMapper.getById(id)).thenReturn(employee);

        // when
        Employee result = employeeService.getById(id);

        // then
        assertEquals("****", result.getPassword());
    }

    @Test
    @DisplayName("启用/禁用员工：更新状态")
    void testStartOrStop_updatesStatus() {
        // given
        Long id = 5L;
        Integer status = StatusConstant.DISABLE;

        // when
        employeeService.startOrStop(status, id);

        // then
        verify(employeeMapper, times(1)).update(argThat(e ->
                e.getId().equals(id) && e.getStatus().equals(status)
        ));
    }
}
