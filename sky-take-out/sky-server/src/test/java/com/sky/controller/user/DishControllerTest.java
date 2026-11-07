package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 菜品缓存单元测试（缓存击穿 + 缓存穿透）
 *
 * <p>覆盖点：
 * <ul>
 *     <li>缓存命中：直接返回缓存，不查库、不加锁</li>
 *     <li>缓存未命中 + 查库有数据：写缓存并返回</li>
 *     <li>缓存穿透：查库结果为空，写入空值标记（短 TTL），返回空列表</li>
 *     <li>缓存击穿（并发）：多个线程同时请求同一分类，只允许一个线程查库重建缓存</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DishControllerTest {

    @Mock
    private DishService dishService;

    @Mock
    private RedisTemplate redisTemplate;

    @Mock
    private ValueOperations valueOperations;

    @InjectMocks
    private DishController dishController;

    @BeforeEach
    void setUp() {
        // RedisTemplate 的 opsForValue() 返回 mock 的 ValueOperations
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("缓存命中：直接返回缓存，不查库、不加锁")
    void testList_cacheHit() {
        List<DishVO> cached = Arrays.asList(new DishVO());
        when(valueOperations.get("dish_1")).thenReturn(cached);

        Result<List<DishVO>> result = dishController.list(1L);

        assertEquals(1, result.getCode());
        assertEquals(cached, result.getData());
        // 缓存命中，不查库、不加锁
        verify(dishService, never()).listWithFlavor(any());
        verify(valueOperations, never()).setIfAbsent(anyString(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("缓存未命中 + 查库有数据：写缓存并返回")
    void testList_missThenLoadFromDb() {
        List<DishVO> fromDb = Arrays.asList(new DishVO(), new DishVO());
        when(valueOperations.get("dish_1")).thenReturn(null);              // 首次 miss
        when(valueOperations.setIfAbsent("lock_dish_1", "1", 10, TimeUnit.SECONDS)).thenReturn(true); // 拿到锁
        when(dishService.listWithFlavor(any(Dish.class))).thenReturn(fromDb);

        Result<List<DishVO>> result = dishController.list(1L);

        assertEquals(1, result.getCode());
        assertEquals(fromDb, result.getData());
        // 查了一次库，写了一次缓存，最后释放锁
        verify(dishService, times(1)).listWithFlavor(any(Dish.class));
        verify(valueOperations, times(1)).set(eq("dish_1"), eq(fromDb), anyLong(), eq(TimeUnit.MINUTES));
        verify(redisTemplate, times(1)).delete("lock_dish_1");
    }

    @Test
    @DisplayName("缓存穿透：查库结果为空，写空值标记（短 TTL 5 分钟）并返回空列表")
    void testList_penetrationWriteEmptyMarker() {
        when(valueOperations.get("dish_1")).thenReturn(null);
        when(valueOperations.setIfAbsent("lock_dish_1", "1", 10, TimeUnit.SECONDS)).thenReturn(true);
        when(dishService.listWithFlavor(any(Dish.class))).thenReturn(Collections.emptyList());

        Result<List<DishVO>> result = dishController.list(1L);

        assertEquals(1, result.getCode());
        assertTrue(result.getData().isEmpty());
        // 写入空值标记，TTL 为 5 分钟（短于正常缓存）
        verify(valueOperations, times(1)).set(eq("dish_1"), eq(Collections.emptyList()), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("缓存击穿（并发）：多个线程同时请求，查库只发生一次，其余命中缓存")
    void testList_cacheBreakdownConcurrent() throws Exception {
        // 模拟场景：缓存已失效，N 个线程同时请求同一分类。
        // 用 AtomicReference 充当「内存缓存」，查库成功后写入，后续线程能读到，真实模拟击穿防护。
        List<DishVO> fromDb = Arrays.asList(new DishVO());
        AtomicReference<List<DishVO>> cache = new AtomicReference<>(null);
        AtomicInteger dbQueryCount = new AtomicInteger(0);

        // get：从「内存缓存」读（首次为 null，查库写入后非 null）
        when(valueOperations.get("dish_1")).thenAnswer(inv -> cache.get());

        // setIfAbsent 互斥锁：同一时刻只允许一个线程拿到 true（用 CAS 模拟原子性）
        AtomicInteger lockHolder = new AtomicInteger(0);
        when(valueOperations.setIfAbsent("lock_dish_1", "1", 10, TimeUnit.SECONDS))
                .thenAnswer(inv -> lockHolder.compareAndSet(0, 1));

        // 查库：记录查库次数，并写入「内存缓存」（模拟重建缓存）
        when(dishService.listWithFlavor(any(Dish.class))).thenAnswer(inv -> {
            dbQueryCount.incrementAndGet();
            Thread.sleep(20);           // 放大竞争窗口
            cache.set(fromDb);          // 模拟写缓存
            return fromDb;
        });

        // 释放锁：把锁置回 0（finally 里的 redisTemplate.delete）
        doAnswer(inv -> {
            lockHolder.set(0);
            return null;
        }).when(redisTemplate).delete("lock_dish_1");

        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    dishController.list(1L);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();              // 同时放行所有线程，制造并发
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        // 核心断言：查库只发生一次（击穿防护生效），所有线程正常返回
        assertEquals(1, dbQueryCount.get(), "并发击穿时查库应只发生一次");
        assertEquals(threadCount, successCount.get(), "所有线程都应正常返回");
    }
}
