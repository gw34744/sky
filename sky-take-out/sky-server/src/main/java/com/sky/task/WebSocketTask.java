package com.sky.task;

import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * WebSocket 定时任务
 */
@Component
@Slf4j
public class WebSocketTask {
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 每隔 5 秒向用户端客户端发送服务端心跳消息
     *
     * <p>心跳用于用户端（小程序）探活。管理端（浏览器）自带断线重连机制，
     * 无需服务端心跳，且群发会误触发管理端弹窗，故此处仅推送给用户端会话
     * （sid 为纯数字 userId），避免打扰管理端。
     */
    @Scheduled(cron = "0/5 * * * * ?")
    public void sendMessageToClient() {
        String time = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now());
        String message = "{\"type\":0,\"content\":\"服务端心跳：" + time + "\"}";
        webSocketServer.sendToUserClients(message);
        log.debug("[WebSocket] 心跳推送完成，在线用户端客户端数={}", webSocketServer.onlineCount());
    }
}
