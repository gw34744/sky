package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 根据分类id查询菜品
     *
     * <p>缓存击穿防护：使用 Redis setIfAbsent 互斥锁，保证缓存失效的瞬间只有一个线程去查库并重建缓存，
     * 其余线程短暂等待后重试读取缓存，避免大量请求同时打到数据库。
     *
     * <p>缓存穿透防护（空值缓存）：当分类下没有菜品（或分类 id 本身不存在）时，查询结果为空列表，
     * 若不缓存空值，每次请求都会穿透到数据库做空查询。这里将空结果也写入一个短过期时间的「空值标记」，
     * 下次命中该标记直接返回空，避免恶意构造不存在的分类 id 反复打穿数据库。
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
//        构造redis中的key，规则：dish_分类Id
        String key = "dish_" + categoryId;

//        查询redis中是否存在菜品数据
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        if (list != null && list.size() > 0) {
//            如果存在，直接返回，无需查询数据库
            return Result.success(list);
        }

//        缓存未命中，尝试获取互斥锁，防止缓存击穿
        String lockKey = "lock_" + key;
        try {
//            自旋重试：抢不到锁说明已有线程在重建缓存，稍等片刻后重读缓存
            while (Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS))) {
                Thread.sleep(50);
                list = (List<DishVO>) redisTemplate.opsForValue().get(key);
                if (list != null && list.size() > 0) {
//                    等待期间缓存已被重建，直接返回
                    return Result.success(list);
                }
            }

//            成功获取锁，二次校验（double check），避免拿到锁后缓存已被其他线程写入
            list = (List<DishVO>) redisTemplate.opsForValue().get(key);
            if (list != null && list.size() > 0) {
                return Result.success(list);
            }

//            查询数据库并重建缓存
            Dish dish = new Dish();
            dish.setCategoryId(categoryId);
            dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品
            list = dishService.listWithFlavor(dish);

            if (list == null || list.isEmpty()) {
//                缓存穿透防护：查库结果为空，缓存一个「空值标记」，短过期时间（如 5 分钟）。
//                这样恶意构造不存在的分类 id 时，后续请求命中空标记直接返回，不再打库。
//                注意：空值过期时间要短于正常缓存，避免分类后续新增菜品后长时间读不到。
                redisTemplate.opsForValue().set(key, EMPTY_LIST, EMPTY_TTL_MINUTES, TimeUnit.MINUTES);
                return Result.success(Collections.emptyList());
            }

//            写入缓存，并设置随机过期时间：基础 30 分钟 + 0~10 分钟随机值。
//            1) 解决缓存无限膨胀占用内存的容量问题；
//            2) 随机值避免大量 key 在同一时刻同时过期，防止缓存雪崩；
//            3) 过期时间同时作为「缓存与数据库一致性」的兜底手段。
            long ttl = 30 + ThreadLocalRandom.current().nextInt(10);
            redisTemplate.opsForValue().set(key, list, ttl, TimeUnit.MINUTES);
            return Result.success(list);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("查询菜品数据被中断", e);
        } finally {
//            释放互斥锁，避免锁残留导致后续请求一直自旋
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 空值缓存标记：用不可变的空列表占位，防止缓存穿透
     */
    private static final List<DishVO> EMPTY_LIST = Collections.emptyList();

    /**
     * 空值缓存过期时间（分钟）：短于正常缓存，避免分类新增菜品后长时间读不到
     */
    private static final long EMPTY_TTL_MINUTES = 5;


}
