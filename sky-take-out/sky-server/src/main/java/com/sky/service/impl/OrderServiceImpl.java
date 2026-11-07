package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.WsMessageConstant;
import com.sky.context.BaseContext;
import com.sky.context.TraceIdUtil;
import com.sky.config.RabbitMQConfiguration;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.enumeration.OrderStatus;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.SnowflakeIdUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;

    @Autowired
    private WebSocketServer webSocketServer;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 用户下单
     *
     * <p>熔断：当数据库/下游调用连续失败率达到阈值时，熔断器打开，快速失败并走降级，
     * 避免雪崩（下单是核心链路，保护它不被异常流量拖垮）。
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "orderService", fallbackMethod = "submitOrderFallback")
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
//        异常情况的处理（收货地址为空、超出配送氛围、购物车为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

//        检查用户的收货地址是否超出配送范围
        checkOutOfRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        Long currentId = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = new ShoppingCart();
//        只能查询当前用户数据
        shoppingCart.setUserId(currentId);

//        查询当前用户的购物车数据
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //构造订单数据
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,order);
        order.setPhone(addressBook.getPhone());
        order.setAddress(addressBook.getDetail());
        order.setConsignee(addressBook.getConsignee());
        order.setNumber(SnowflakeIdUtil.nextIdStr());
        order.setUserId(currentId);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());
        orderMapper.insert(order);

//        订单明细数据
        ArrayList<OrderDetail> orderDetailList = new ArrayList<>();
        shoppingCartList.forEach(cart->{
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        });

//        向明细表中查询n条数据
        orderDetailMapper.insertBatch(orderDetailList);

//        清理购物车中的数据
        shoppingCartMapper.deleteByUserId(currentId);

//        封装返回结果
        OrderSubmitVO submitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();

        return submitVO;
    }

    /**
     * 下单熔断降级：熔断器打开或下单异常时，抛出业务异常快速失败，
     * 由全局异常处理器统一返回友好提示，避免请求堆积拖垮系统。
     *
     * <p>注意：fallback 方法签名须与原方法一致，末尾加 Throwable 接收异常。
     */
    public OrderSubmitVO submitOrderFallback(OrdersSubmitDTO ordersSubmitDTO, Throwable throwable) {
        log.error("下单熔断降级触发，订单服务异常：{}", throwable.getMessage());
        throw new OrderBusinessException("系统繁忙，下单暂时不可用，请稍后再试");
    }

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     * @throws Exception
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
//        当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(String.valueOf(userId));

        String orderNumber = ordersPaymentDTO.getOrderNumber();
        Orders orders = orderMapper.getByNumberAndUserId(orderNumber, userId);

//        调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(),
                orders.getAmount(),
                "苍穹外卖订单" + orders.getId(),
                user.getOpenid()
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * <p>支付回调异步化：本方法只做「幂等校验 + 落库」这两件最小必要操作，保证快速返回，
     * 避免微信因超时重试；「来单提醒」等后续动作通过 RabbitMQ 异步解耦，由消费者推送 WebSocket。
     *
     * @param outTradeNo    商户订单号
     * @param transactionId 微信支付交易号
     */
    @Override
    public void paySuccess(String outTradeNo, String transactionId) {
//        根据订单号查询订单（支付回调无登录态，不能用 BaseContext 里的 userId）
        Orders orderDB = orderMapper.getByNumber(outTradeNo);
        if (orderDB == null) {
            log.error("支付回调：订单不存在，订单号：{}", outTradeNo);
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

//        幂等判断：订单已是已支付状态，说明是微信的重复回调，直接返回，不重复处理
        if (Orders.PAID.equals(orderDB.getPayStatus())) {
            log.info("支付回调：订单已处理过（重复回调），忽略，订单号：{}", outTradeNo);
            return;
        }

//        根据订单id更新订单的状态、支付状态、结账时间、微信交易号
        Orders orders = Orders.builder()
                .id(orderDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .transactionId(transactionId)
                .checkoutTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);

//        支付回调异步化：发送「来单提醒」消息到 MQ，由消费者异步推送 WebSocket，
//        不在回调线程里做推送，避免后续动作变重时拖慢微信回调响应导致超时重试
        String comingMsg = buildMessage(WsMessageConstant.TYPE_ORDER_COMING,
                orders.getId(), outTradeNo, orders.getStatus(),
                orderDB.getDeliveryStatus(), "新订单来单，请及时接单");
        // 链路追踪：把当前线程的 traceId 透传到消息头，消费者线程取出后重新注入 MDC，
        // 实现「支付回调 → MQ 消费 → WebSocket 推送」整条异步链路日志可串联
        rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.ORDER_NOTIFY_EXCHANGE,
                RabbitMQConfiguration.ORDER_NOTIFY_ROUTING_KEY,
                comingMsg,
                message -> {
                    message.getMessageProperties().setHeader(
                            TraceIdUtil.TRACE_ID_HEADER, TraceIdUtil.getTraceId());
                    return message;
                });
        log.info("支付回调：来单提醒消息已发送 MQ，订单号：{}", outTradeNo);
    }

    /**
     * 历史订单查询
     *
     * @param pageNum
     * @param pageSize
     * @param status   订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     * @return
     */
    @Override
    public PageResult pageQueryForUser(int pageNum, int pageSize, Integer status) {
//        设置分页
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

//        分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        ArrayList<OrderVO> list = new ArrayList<>();

//        查询出订单明细，并封装入OrderVo进行响应
        if (page != null && page.getTotal() > 0) {
            page.forEach(orders -> {
                Long ordersId = orders.getId();

//                查询订单明细
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(ordersId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                list.add(orderVO);
            });
        }

        return new PageResult(page.getTotal(), list);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
//        根据id查询订单
        Orders orders = orderMapper.getById(id);

//        查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

//        将订单及其详情封装到OrderVo并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户取消订单
     * @param id
     */
    @Override
    public void userCancelById(Long id) throws Exception {
//        根据id查询订单
        Orders orderDB = orderMapper.getById(id);

//        校验订单是否存在
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

//      订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (orderDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(orderDB.getId());

//        订单处于待接单的状态下取消，需要进行退款
        if (orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
//            调用微信支付退款接口
            weChatPayUtil.refund(
                    orderDB.getNumber(),
                    orderDB.getNumber(),
                    orders.getAmount(),
                    orders.getAmount()
            );

//            支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

//        更新订单状态，取消原因、时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 再来一单
     * @param id
     */
    @Override
    public void repetition(Long id) {
//        查询当前用户id
        Long userId = BaseContext.getCurrentId();

//        根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

//        将订单详情对象转为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(orderDetail -> {
            ShoppingCart shoppingCart = new ShoppingCart();

//            将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

//        将购物车对象批量添加到购物车
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> pageQuery = orderMapper.pageQuery(ordersPageQueryDTO);

//        部分订单状态，需要额外返回订单菜品信息，将orders转化为orderVo
        List<OrderVO> orderVoList = getOrderVoList(pageQuery);
        return new PageResult(pageQuery.getTotal(), orderVoList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
//        根据状态，分别查询出接待单，待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

//        将查询出的数据封装
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);

        return orderStatisticsVO;
    }

    /**
     * 接单
     * @param ordersCancelDTO
     */
    @Override
    public void confirm(OrdersCancelDTO ordersCancelDTO) {
        Orders orderDB = orderMapper.getById(ordersCancelDTO.getId());
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 状态机校验：待接单 → 已接单
        OrderStatus.checkTransition(OrderStatus.of(orderDB.getStatus()), OrderStatus.CONFIRMED);

        Orders orders = Orders.builder()
                .id(ordersCancelDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);

//        推送「已接单」状态给下单用户
        pushStatus(orderDB.getUserId(), ordersCancelDTO.getId(), orderDB.getNumber(),
                Orders.CONFIRMED, orderDB.getDeliveryStatus(), "商家已接单，正在备餐");
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
//        根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

//        订单只有存在且状态为2（待接单）才可以拒单
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 状态机校验：待接单 → 已取消（拒单）
        OrderStatus.checkTransition(OrderStatus.of(ordersDB.getStatus()), OrderStatus.CANCELLED);

//        支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
//            用户已支付，需要退款
            String refund = weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    ordersDB.getAmount(),
                    ordersDB.getAmount()
            );
            log.info("申请退款：{}", refund);
        }

//        拒单需要退款，根据订单id更新订单状态，拒单原因，取消时间
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);

//        推送「订单被拒」状态给下单用户
        pushStatus(ordersDB.getUserId(), ordersDB.getId(), ordersDB.getNumber(),
                Orders.CANCELLED, ordersDB.getDeliveryStatus(), "订单已被商家拒绝：" + ordersRejectionDTO.getRejectionReason());
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
//        根据id查询订单
        Orders orderDB = orderMapper.getById(ordersCancelDTO.getId());

//        支付状态
        Integer payStatus = orderDB.getPayStatus();
        if (payStatus == 1) {
//            用于已支付，需要退款
            String refund = weChatPayUtil.refund(
                    orderDB.getNumber(),
                    orderDB.getNumber(),
                    orderDB.getAmount(),
                    orderDB.getAmount()
            );
            log.info("申请退款：{}", refund);
        }

//      管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
//        根据id查询订单
        Orders orderDB = orderMapper.getById(id);

//        校验订单是否存在，并且状态为3
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 状态机校验：已接单 → 派送中
        OrderStatus.checkTransition(OrderStatus.of(orderDB.getStatus()), OrderStatus.DELIVERY_IN_PROGRESS);

        Orders orders = new Orders();
        orders.setId(orderDB.getId());

//        更新订单状态，状态转为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);

//        推送「骑手派送中」状态给下单用户；文案区分立即送出/预定时间
        String deliveryTip = Objects.equals(orderDB.getDeliveryStatus(), WsMessageConstant.DELIVERY_IMMEDIATE)
                ? "骑手已取餐，正在配送中"
                : "订单已按预定时间送出，骑手配送中";
        pushStatus(orderDB.getUserId(), orderDB.getId(), orderDB.getNumber(),
                Orders.DELIVERY_IN_PROGRESS, orderDB.getDeliveryStatus(), deliveryTip);
    }

    /**
     * 完成订单
     * @param id
     */
    @Override
    public void complete(Long id) {
//        根据id查询订单
        Orders orderDB = orderMapper.getById(id);

//        校验订单是否存在，并且状态为4
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 状态机校验：派送中 → 已完成
        OrderStatus.checkTransition(OrderStatus.of(orderDB.getStatus()), OrderStatus.COMPLETED);

        Orders orders = new Orders();
        orders.setId(orderDB.getId());

//        更新订单状态，状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);

//        推送「订单已完成」状态给下单用户
        pushStatus(orderDB.getUserId(), orderDB.getId(), orderDB.getNumber(),
                Orders.COMPLETED, orderDB.getDeliveryStatus(), "订单已送达，用餐愉快");
    }

    /**
     * 用户催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
//        查询订单是否存在
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

//        基于WebSocket实现催单，向管理端推送
        String remindMsg = buildMessage(WsMessageConstant.TYPE_REMINDER,
                orders.getId(), orders.getNumber(), orders.getStatus(),
                orders.getDeliveryStatus(), "用户催单，请尽快处理");
        webSocketServer.sendToAllClient(remindMsg);
    }

    /**
     * 部分订单状态，需要额外返回订单菜品信息，将orders转化为orderVo
     * @param page
     * @return
     */
    private List<OrderVO> getOrderVoList(Page<Orders> page) {
//        需要返回订单菜品信息，自定义OrderVo响应结果
        ArrayList<OrderVO> orderVOArrayList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (!CollectionUtils.isEmpty(ordersList)) {
            ordersList.forEach(orders -> {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                String orderDishStr = getOrderDishStr(orders);
                orderVO.setOrderDishes(orderDishStr);
                orderVOArrayList.add(orderVO);
            });
        }
        return orderVOArrayList;
    }

    /**
     * 根据订单id获取菜品信息字符串
     * @param orders
     * @return
     */
    private String getOrderDishStr(Orders orders) {
//        查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

//        将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> ordewrDishList = orderDetailList.stream().map(orderDetail -> orderDetail.getName() + "*" + orderDetail.getNumber() + ";").collect(Collectors.toList());

//        将该订单对应的所有菜品信息拼接在一起
        return String.join("", ordewrDishList);
    }

    /**
     * 订单状态变更，定向推送给下单用户
     *
     * @param userId         下单用户id（作为 WebSocket 的 sid）
     * @param orderId        订单id
     * @param orderNumber    订单号
     * @param status         变更后的订单状态
     * @param deliveryStatus 配送方式（1立即送出 0预定时间）
     * @param content        提示文案
     */
    private void pushStatus(Long userId, Long orderId, String orderNumber,
                            Integer status, Integer deliveryStatus, String content) {
        if (userId == null) {
            log.warn("[WebSocket] 订单 {} 未关联用户，无法定向推送", orderNumber);
            return;
        }
        String message = buildMessage(WsMessageConstant.TYPE_STATUS_CHANGE,
                orderId, orderNumber, status, deliveryStatus, content);
        webSocketServer.sendToUser(String.valueOf(userId), message);
    }

    /**
     * 构建统一的 WebSocket 推送消息 JSON
     *
     * @param type           消息类型，见 {@code WsMessageConstant}
     * @param orderId        订单id
     * @param orderNumber    订单号
     * @param status         订单状态
     * @param deliveryStatus 配送方式
     * @param content        提示文案
     * @return JSON 字符串
     */
    private String buildMessage(Integer type, Long orderId, String orderNumber,
                                Integer status, Integer deliveryStatus, String content) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("orderId", orderId);
        map.put("orderNumber", orderNumber);
        map.put("status", status);
        map.put("deliveryStatus", deliveryStatus);
        map.put("content", content);
        map.put("timestamp", System.currentTimeMillis());
        return JSON.toJSONString(map);
    }

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        HashMap map = new HashMap();
        map.put("address", shopAddress);
        map.put("output", "json");
        map.put("ak", ak);

//        获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("店铺地址解析失败");
        }

//        数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");

//        店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address", address);

//        获取用户地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

//        数据解析
        location = JSON.parseObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");

//        用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("orgin", shopLngLat);
        map.put("destination", userLngLat);
        map.put("steps_info", "0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("配送线路规划失败");
        }

//        数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }
}
