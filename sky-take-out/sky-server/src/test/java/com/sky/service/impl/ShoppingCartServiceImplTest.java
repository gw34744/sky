package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 购物车业务层单元测试
 *
 * <p>覆盖点：
 * <ul>
 *     <li>添加购物车：菜品（不存在→插入）、套餐、已存在→数量+1</li>
 *     <li>删除购物车：数量=1 直接删记录、数量>1 减一</li>
 *     <li>清空购物车</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ShoppingCartServiceImplTest {

    @Mock
    private ShoppingCartMapper shoppingCartMapper;

    @Mock
    private DishMapper dishMapper;

    @Mock
    private SetmealMapper setmealMapper;

    @InjectMocks
    private ShoppingCartServiceImpl shoppingCartService;

    @BeforeEach
    void setUp() {
        // BaseContext 是 ThreadLocal，直接设置当前用户 id
        BaseContext.setCurrentId(1001L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    // ==================== 添加购物车 ====================

    @Test
    @DisplayName("添加菜品：购物车无该商品时插入，带菜品名称/图片/价格")
    void testAddShoppingCart_dish_new() {
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(1L);

        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Collections.emptyList());
        Dish dish = Dish.builder().id(1L).name("宫保鸡丁").image("xx.jpg")
                .price(new BigDecimal("28.00")).build();
        when(dishMapper.getById(1L)).thenReturn(dish);

        shoppingCartService.addShoppingCart(dto);

        verify(shoppingCartMapper, times(1)).insert(argThat(sc ->
                "宫保鸡丁".equals(sc.getName())
                        && sc.getNumber().equals(1)
                        && sc.getUserId().equals(1001L)
        ));
        verify(shoppingCartMapper, never()).updateNumberById(any());
    }

    @Test
    @DisplayName("添加套餐：购物车无该商品时插入，带套餐名称/图片/价格")
    void testAddShoppingCart_setmeal_new() {
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setSetmealId(10L);

        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Collections.emptyList());
        Setmeal setmeal = Setmeal.builder().id(10L).name("超值套餐").image("s.jpg")
                .price(new BigDecimal("58.00")).build();
        when(setmealMapper.getById(10L)).thenReturn(setmeal);

        shoppingCartService.addShoppingCart(dto);

        verify(shoppingCartMapper, times(1)).insert(argThat(sc ->
                "超值套餐".equals(sc.getName()) && sc.getNumber().equals(1)));
        verify(dishMapper, never()).getById(any());
    }

    @Test
    @DisplayName("添加购物车：商品已存在时数量+1，不重复插入")
    void testAddShoppingCart_alreadyExist() {
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(1L);

        ShoppingCart existing = ShoppingCart.builder().id(100L).number(2).build();
        when(shoppingCartMapper.list(any(ShoppingCart.class)))
                .thenReturn(Collections.singletonList(existing));

        shoppingCartService.addShoppingCart(dto);

        // 数量从 2 变 3
        verify(shoppingCartMapper, times(1)).updateNumberById(argThat(sc -> sc.getNumber().equals(3)));
        verify(shoppingCartMapper, never()).insert(any());
    }

    // ==================== 删除购物车 ====================

    @Test
    @DisplayName("删除购物车：数量为1直接删除记录")
    void testSubShoppingCart_deleteWhenOne() {
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(1L);

        ShoppingCart cart = ShoppingCart.builder().id(100L).number(1).build();
        when(shoppingCartMapper.list(any(ShoppingCart.class)))
                .thenReturn(Collections.singletonList(cart));

        shoppingCartService.subShoppingCart(dto);

        verify(shoppingCartMapper, times(1)).deleteById(100L);
        verify(shoppingCartMapper, never()).updateNumberById(any());
    }

    @Test
    @DisplayName("删除购物车：数量大于1时减一")
    void testSubShoppingCart_decreaseWhenMoreThanOne() {
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(1L);

        ShoppingCart cart = ShoppingCart.builder().id(100L).number(3).build();
        when(shoppingCartMapper.list(any(ShoppingCart.class)))
                .thenReturn(Collections.singletonList(cart));

        shoppingCartService.subShoppingCart(dto);

        verify(shoppingCartMapper, times(1)).updateNumberById(argThat(sc -> sc.getNumber().equals(2)));
        verify(shoppingCartMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("删除购物车：商品不存在时不做任何操作")
    void testSubShoppingCart_notExist() {
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(1L);

        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Collections.emptyList());

        shoppingCartService.subShoppingCart(dto);

        verify(shoppingCartMapper, never()).deleteById(any());
        verify(shoppingCartMapper, never()).updateNumberById(any());
    }

    // ==================== 清空购物车 ====================

    @Test
    @DisplayName("清空购物车：按当前用户 id 删除")
    void testCleanShoppingCart() {
        shoppingCartService.cleanShoppingCart();

        verify(shoppingCartMapper, times(1)).deleteByUserId(1001L);
    }

    // ==================== 查看购物车 ====================

    @Test
    @DisplayName("查看购物车：按当前用户 id 查询")
    void testShowShoppingCart() {
        when(shoppingCartMapper.list(any(ShoppingCart.class)))
                .thenReturn(Collections.emptyList());

        List<ShoppingCart> result = shoppingCartService.showShoppingCart();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(shoppingCartMapper, times(1)).list(argThat(sc -> sc.getUserId().equals(1001L)));
    }
}
