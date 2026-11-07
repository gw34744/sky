package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.vo.DishVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 菜品业务层单元测试
 *
 * <p>覆盖点：
 * <ul>
 *     <li>批量删除：起售中不可删、被套餐关联不可删、正常删除（含口味级联删除）</li>
 *     <li>起售/停售：停售联动停售关联套餐</li>
 *     <li>查询：根据 id 查菜品+口味、根据条件查菜品+口味列表</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DishServiceImplTest {

    @Mock
    private DishMapper dishMapper;

    @Mock
    private DishFlavorMapper dishFlavorMapper;

    @Mock
    private SetmealDishMapper setmealDishMapper;

    @Mock
    private SetmealMapper setmealMapper;

    @InjectMocks
    private DishServiceImpl dishService;

    // ==================== 批量删除 ====================

    @Test
    @DisplayName("批量删除：起售中的菜品不能删除")
    void testDeleteBatch_onSale() {
        Dish dish = Dish.builder().id(1L).status(StatusConstant.ENABLE).build();
        when(dishMapper.getById(1L)).thenReturn(dish);

        assertThrows(DeletionNotAllowedException.class,
                () -> dishService.deleteBatch(Collections.singletonList(1L)));

        // 起售中直接抛异常，不应继续删库
        verify(dishMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("批量删除：被套餐关联的菜品不能删除")
    void testDeleteBatch_relatedBySetmeal() {
        Dish dish = Dish.builder().id(1L).status(StatusConstant.DISABLE).build();
        when(dishMapper.getById(1L)).thenReturn(dish);
        when(setmealDishMapper.getSetmealIdsByDishIds(anyList()))
                .thenReturn(Arrays.asList(101L, 102L));

        assertThrows(DeletionNotAllowedException.class,
                () -> dishService.deleteBatch(Collections.singletonList(1L)));

        verify(dishMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("批量删除：停售且未被套餐关联，正常删除并级联删口味")
    void testDeleteBatch_success() {
        Dish dish = Dish.builder().id(1L).status(StatusConstant.DISABLE).build();
        when(dishMapper.getById(1L)).thenReturn(dish);
        when(setmealDishMapper.getSetmealIdsByDishIds(anyList()))
                .thenReturn(Collections.emptyList());

        dishService.deleteBatch(Collections.singletonList(1L));

        verify(dishMapper, times(1)).deleteById(1L);
        verify(dishFlavorMapper, times(1)).deleteByDishId(1L);
    }

    // ==================== 起售/停售 ====================

    @Test
    @DisplayName("起售：仅更新菜品状态，不联动套餐")
    void testStartOrStop_enable() {
        dishService.startOrStop(StatusConstant.ENABLE, 1L);

        verify(dishMapper, times(1)).update(argThat(d ->
                d.getId().equals(1L) && d.getStatus().equals(StatusConstant.ENABLE)));
        // 起售不查询套餐关联，也不更新套餐
        verify(setmealDishMapper, never()).getSetmealIdsByDishIds(anyList());
        verify(setmealMapper, never()).update(any());
    }

    @Test
    @DisplayName("停售：联动停售包含该菜品的套餐")
    void testStartOrStop_disable() {
        when(setmealDishMapper.getSetmealIdsByDishIds(anyList()))
                .thenReturn(Arrays.asList(101L, 102L));

        dishService.startOrStop(StatusConstant.DISABLE, 1L);

        verify(dishMapper, times(1)).update(argThat(d ->
                d.getId().equals(1L) && d.getStatus().equals(StatusConstant.DISABLE)));
        // 两个关联套餐都被停售
        verify(setmealMapper, times(2)).update(argThat(s ->
                s.getStatus().equals(StatusConstant.DISABLE)));
    }

    @Test
    @DisplayName("停售：无关联套餐时不更新套餐")
    void testStartOrStop_disable_noSetmeal() {
        when(setmealDishMapper.getSetmealIdsByDishIds(anyList()))
                .thenReturn(Collections.emptyList());

        dishService.startOrStop(StatusConstant.DISABLE, 1L);

        verify(dishMapper, times(1)).update(any());
        verify(setmealMapper, never()).update(any());
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("根据 id 查询菜品及口味")
    void testGetByIdWithFlavor() {
        Dish dish = Dish.builder().id(1L).name("宫保鸡丁").price(new BigDecimal("28.00")).build();
        List<DishFlavor> flavors = Arrays.asList(
                DishFlavor.builder().dishId(1L).name("辣度").value("不辣,微辣,中辣").build()
        );
        when(dishMapper.getById(1L)).thenReturn(dish);
        when(dishFlavorMapper.getByDishId(1L)).thenReturn(flavors);

        DishVO vo = dishService.getByIdWithFlavor(1L);

        assertNotNull(vo);
        assertEquals("宫保鸡丁", vo.getName());
        assertEquals(1, vo.getFlavors().size());
    }

    @Test
    @DisplayName("根据条件查询菜品及口味列表")
    void testListWithFlavor() {
        Dish d1 = Dish.builder().id(1L).name("宫保鸡丁").build();
        Dish d2 = Dish.builder().id(2L).name("鱼香肉丝").build();
        when(dishMapper.list(any(Dish.class))).thenReturn(Arrays.asList(d1, d2));
        when(dishFlavorMapper.getByDishId(anyLong())).thenReturn(Collections.emptyList());

        List<DishVO> result = dishService.listWithFlavor(new Dish());

        assertEquals(2, result.size());
        // 每个菜品都查询了一次口味
        verify(dishFlavorMapper, times(2)).getByDishId(anyLong());
    }
}
