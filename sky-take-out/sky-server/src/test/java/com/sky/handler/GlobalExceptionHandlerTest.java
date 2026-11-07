package com.sky.handler;

import com.sky.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 全局异常处理器单元测试（参数校验边界）
 *
 * <p>覆盖点：
 * <ul>
 *     <li>参数校验异常（MethodArgumentNotValidException）→ 返回第一个校验失败字段的错误信息</li>
 *     <li>校验异常无字段错误时的兜底信息</li>
 * </ul>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("参数校验失败：返回第一个字段错误信息")
    void testMethodArgumentNotValidException() throws Exception {
        // 构造一个带字段错误的 MethodArgumentNotValidException
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("ordersSubmitDTO", "addressBookId", "收货地址不能为空");
        when(bindingResult.getFieldErrors()).thenReturn(Collections.singletonList(fieldError));

        Method method = this.getClass().getDeclaredMethod("dummyMethod");
        MethodParameter parameter = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        Result result = handler.exceptionHandler(ex);

        assertEquals(0, result.getCode());
        assertEquals("收货地址不能为空", result.getMsg());
    }

    @Test
    @DisplayName("参数校验异常无字段错误：返回兜底信息「参数校验失败」")
    void testMethodArgumentNotValidException_noFieldError() throws Exception {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(Collections.emptyList());

        Method method = this.getClass().getDeclaredMethod("dummyMethod");
        MethodParameter parameter = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        Result result = handler.exceptionHandler(ex);

        assertEquals(0, result.getCode());
        assertEquals("参数校验失败", result.getMsg());
    }

    /**
     * 占位方法：仅用于构造 MethodParameter
     */
    @SuppressWarnings("unused")
    private void dummyMethod() {
    }
}
