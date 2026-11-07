package com.sky.controller.user;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 订单
 */
@RestController("userOrderController")
@RequestMapping("/user/order")
@Slf4j
@Api(tags = "C端-订单接口")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 获取下单 Token（防重复提交）
     *
     * <p>用户在下单前先调用本接口获取一次性 token，提交订单时携带该 token，
     * 后端通过 setIfAbsent 原子校验并删除，保证同一用户同一时刻只有一次下单能成功，实现接口幂等。
     *
     * @return 一次性下单 token
     */
    @GetMapping("/submitToken")
    @ApiOperation("获取下单 Token（防重复提交）")
    public Result<String> submitToken() {
        Long userId = com.sky.context.BaseContext.getCurrentId();
        String token = UUID.randomUUID().toString().replace("-", "");
        // key 规则：submit_token_{userId}，设置 10 分钟过期，防止 token 长期占用内存
        redisTemplate.opsForValue().set("submit_token_" + userId, token, 10, TimeUnit.MINUTES);
        return Result.success(token);
    }

    /**
     * 用户下单
     *
     * <p>幂等校验：携带 submitToken 提交，后端原子校验并删除 token，重复提交将被拒绝。
     *
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO,
                                        @RequestHeader(value = "submitToken", required = false) String submitToken) {
        log.info("用户下单：{}", ordersSubmitDTO);

//        幂等校验：使用 setIfAbsent 原子删除 token，保证并发下只有一个请求能通过校验
        Long userId = com.sky.context.BaseContext.getCurrentId();
        String tokenKey = "submit_token_" + userId;
        Boolean removed = redisTemplate.delete(tokenKey);
        if (submitToken == null || !Boolean.TRUE.equals(removed)) {
//            token 缺失或已失效（已被使用/过期），判定为重复提交
            return Result.error("请勿重复提交订单");
        }

        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 历史订单查询
     *
     * @param pageNum
     * @param pageSize
     * @param status   订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     * @return
     */
    @GetMapping("historyOrders")
    @ApiOperation("历史订单查询")
    public Result<PageResult> page(int pageNum, int pageSize, Integer status) {
        PageResult pageResult=orderService.pageQueryForUser(pageNum, pageSize, status);
        return Result.success(pageResult);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable("id") Long id) {
        OrderVO orderVO = orderService.details(id);
        return Result.success(orderVO);
    }

    /**
     * 用户取消订单
     * @param id
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("用户取消订单")
    public Result cancel(@PathVariable Long id) throws Exception {
        orderService.userCancelById(id);
        return Result.success();
    }

    /**
     * 再来一单
     * @param id
     * @return
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result repetition(@PathVariable Long id) {
        orderService.repetition(id);
        return Result.success();
    }

    /**
     * 用户催单
     *
     * @param id
     * @return
     */
    @GetMapping("/reminder/{id}")
    @ApiOperation("用户催单")
    public Result reminder(@PathVariable("id") Long id) {
        orderService.reminder(id);
        return Result.success();
    }
}
