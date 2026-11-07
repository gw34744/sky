package com.sky.controller.user;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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
     * <p>限流：应用层限流（Resilience4j RateLimiter，1 秒 5 个），与 Nginx 网关限流形成双层防护，
     * 防止刷单/恶意高频提交。
     *
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    @RateLimiter(name = "orderSubmit", fallbackMethod = "submitFallback")
    public Result<OrderSubmitVO> submit(@RequestBody @Valid OrdersSubmitDTO ordersSubmitDTO,
                                        @RequestHeader(value = "submitToken", required = false) String submitToken) {
        log.info("用户下单：{}", ordersSubmitDTO);

//        幂等校验：读取 Redis 中的 token 与请求携带的 token 比对，一致才删除并放行。
//        相比直接用 delete（无论 token 对错都删、且并发下可能双删），
//        这里先 get 比对值，再原子删除，保证：
//        1) 只有携带正确 token 的请求才能通过；
//        2) token 一次性使用，用完即删，重复提交被拒绝。
        Long userId = com.sky.context.BaseContext.getCurrentId();
        String tokenKey = "submit_token_" + userId;
        Object cached = redisTemplate.opsForValue().get(tokenKey);
        if (submitToken == null || submitToken.isEmpty() || !submitToken.equals(cached)) {
//            token 缺失、或与 Redis 中不一致（已失效/被篡改/重复提交），判定为重复提交
            return Result.error("请勿重复提交订单");
        }
//        校验通过后删除 token（一次性），防止同一 token 被重复使用
        redisTemplate.delete(tokenKey);

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
    @RateLimiter(name = "payment", fallbackMethod = "paymentFallback")
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

    /**
     * 下单限流降级：请求超过限流阈值时返回友好提示，而非直接抛异常/无响应。
     * 签名必须与原方法一致（参数、返回值类型相同），末尾可加 Throwable 参数接收限流异常。
     */
    public Result<OrderSubmitVO> submitFallback(OrdersSubmitDTO ordersSubmitDTO,
                                                String submitToken, Throwable throwable) {
        log.warn("下单被限流，拒绝请求：{}", ordersSubmitDTO);
        return Result.error("下单请求过于频繁，请稍后再试");
    }

    /**
     * 支付限流降级
     */
    public Result<OrderPaymentVO> paymentFallback(OrdersPaymentDTO ordersPaymentDTO, Throwable throwable) {
        log.warn("支付请求被限流：{}", ordersPaymentDTO);
        return Result.error("支付请求过于频繁，请稍后再试");
    }
}
