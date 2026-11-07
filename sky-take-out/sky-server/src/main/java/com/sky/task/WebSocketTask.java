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
     * 每隔 5 秒向所有客户端发送服务端心跳消息
     *
     * <p>用于验证连接存活、前端探活，生产环境可替换为 ping/pong 心跳。
     */
    @Scheduled(cron = "0/5 * * * * ?")
    public void sendMessageToClient() {
        String time = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now());
        String message = "{\"type\":0,\"content\":\"服务端心跳：" + time + "\"}";
        webSocketServer.sendToAllClient(message);
        log.debug("[WebSocket] 心跳推送完成，在线客户端数={}", webSocketServer.onlineCount());
    }
}
