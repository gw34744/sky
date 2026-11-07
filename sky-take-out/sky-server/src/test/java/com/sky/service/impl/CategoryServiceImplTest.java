package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.dto.CategoryDTO;
import com.sky.entity.Category;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 分类业务层单元测试
 *
 * <p>覆盖点：
 * <ul>
 *     <li>新增分类：默认禁用状态、调用 insert</li>
 *     <li>删除分类：关联菜品 / 关联套餐时抛出业务异常</li>
 *     <li>删除分类：无关联时正常删除</li>
 *     <li>启用 / 禁用分类</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private DishMapper dishMapper;

    @Mock
    private SetmealMapper setmealMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        // 每个用例前重置 Mock 交互记录，避免用例间相互干扰
        reset(categoryMapper, dishMapper, setmealMapper);
    }

    @Test
    @DisplayName("新增分类：状态默认为禁用，且正确调用 insert")
    void testSave_setsDefaultDisableStatusAndInserts() {
        // given
        CategoryDTO dto = new CategoryDTO();
        dto.setName("家常菜");
        dto.setType(1);

        // when
        categoryService.save(dto);

        // then
        verify(categoryMapper, times(1)).insert(argThat(c ->
                c.getName().equals("家常菜")
                        && c.getType() == 1
                        && c.getStatus() == StatusConstant.DISABLE
        ));
    }

    @Test
    @DisplayName("删除分类：当前分类下关联了菜品，抛出业务异常")
    void testDeleteById_throwsWhenRelatedByDish() {
        // given
        Long categoryId = 10L;
        when(dishMapper.countByCategoryId(categoryId)).thenReturn(3);

        // when & then
        DeletionNotAllowedException ex = assertThrows(
                DeletionNotAllowedException.class,
                () -> categoryService.deleteById(categoryId)
        );
        assertEquals("当前分类关联了菜品,不能删除", ex.getMessage());
        // 校验：异常分支下不应继续执行删除
        verify(categoryMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("删除分类：当前分类下关联了套餐，抛出业务异常")
    void testDeleteById_throwsWhenRelatedBySetmeal() {
        // given
        Long categoryId = 10L;
        when(dishMapper.countByCategoryId(categoryId)).thenReturn(0);
        when(setmealMapper.countByCategoryId(categoryId)).thenReturn(2);

        // when & then
        DeletionNotAllowedException ex = assertThrows(
                DeletionNotAllowedException.class,
                () -> categoryService.deleteById(categoryId)
        );
        assertEquals("当前分类关联了套餐,不能删除", ex.getMessage());
        verify(categoryMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("删除分类：无关联菜品和套餐，正常删除")
    void testDeleteById_deletesWhenNoRelation() {
        // given
        Long categoryId = 10L;
        when(dishMapper.countByCategoryId(categoryId)).thenReturn(0);
        when(setmealMapper.countByCategoryId(categoryId)).thenReturn(0);

        // when
        categoryService.deleteById(categoryId);

        // then
        verify(categoryMapper, times(1)).deleteById(categoryId);
    }

    @Test
    @DisplayName("启用/禁用分类：更新分类状态")
    void testStartOrStop_updatesStatus() {
        // given
        Long categoryId = 20L;
        Integer status = StatusConstant.ENABLE;

        // when
        categoryService.startOrStop(status, categoryId);

        // then
        verify(categoryMapper, times(1)).update(argThat(c ->
                c.getId().equals(categoryId) && c.getStatus().equals(status)
        ));
    }

    @Test
    @DisplayName("根据类型查询分类列表")
    void testList_returnsCategoriesByType() {
        // given
        Integer type = 1;
        Category category = Category.builder().id(1L).name("家常菜").type(1).build();
        when(categoryMapper.list(type)).thenReturn(java.util.Collections.singletonList(category));

        // when
        java.util.List<Category> result = categoryService.list(type);

        // then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("家常菜", result.get(0).getName());
        verify(categoryMapper, times(1)).list(type);
    }
}
