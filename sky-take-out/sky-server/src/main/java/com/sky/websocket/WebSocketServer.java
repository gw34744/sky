package com.sky.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 服务
 *
 * <p>连接地址约定：{@code /ws/{sid}}，其中 sid 为业务标识。
 * 建议：用户端以 userId 作为 sid，管理端以固定标识（如 "admin"）连接。
 * 通过区分 sid 前缀可实现「定向推送」与「群发」两种能力：
 * <ul>
 *   <li>{@link #sendToUser(String, String)}：按 sid 定向推送给单个客户端（用户端状态推送）</li>
 *   <li>{@link #sendToAllClient(String)}：群发给所有在线客户端（来单提醒/催单提醒）</li>
 * </ul>
 *
 * <p>推送消息统一为 JSON 文本，字段见 {@code WsMessageConstant}。
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    /**
     * 会话集合。使用 ConcurrentHashMap 保证并发安全：
     * 多个业务线程可能同时调用 sendToAllClient/sendToUser 遍历，避免 HashMap 并发修改异常。
     */
    private static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    /**
     * 连接建立成功
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("[WebSocket] 建立连接，sid={}，sessionId={}", sid, session.getId());
        SESSION_MAP.put(sid, session);
    }

    /**
     * 收到客户端消息
     *
     * <p>当前业务为服务端单向推送，客户端一般不发消息；收到消息仅记录日志用于排查心跳/调试。
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("[WebSocket] 收到客户端消息，sid={}，message={}", sid, message);
    }

    /**
     * 连接关闭
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("[WebSocket] 连接断开，sid={}", sid);
        SESSION_MAP.remove(sid);
    }

    /**
     * 连接异常
     */
    @OnError
    public void onError(@PathParam("sid") String sid, Throwable error) {
        log.error("[WebSocket] 连接异常，sid={}", sid, error);
        // 异常时移除会话，避免残留失效连接
        SESSION_MAP.remove(sid);
    }

    /**
     * 群发消息给所有在线客户端
     *
     * @param message JSON 文本消息
     */
    public void sendToAllClient(String message) {
        Collection<Session> sessions = SESSION_MAP.values();
        log.info("[WebSocket] 群发消息，在线客户端数={}，message={}", sessions.size(), message);
        sessions.forEach(session -> send(session, message));
    }

    /**
     * 定向推送消息给指定 sid 的客户端
     *
     * @param sid     客户端业务标识（如 userId）
     * @param message JSON 文本消息
     */
    public void sendToUser(String sid, String message) {
        Session session = SESSION_MAP.get(sid);
        if (session == null) {
            log.info("[WebSocket] 目标客户端不在线，忽略推送，sid={}", sid);
            return;
        }
        send(session, message);
    }

    /**
     * 发送单条消息，统一异常处理
     */
    private void send(Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.error("[WebSocket] 消息发送失败，sessionId={}", session.getId(), e);
            // 发送失败说明连接已失效，主动关闭并移除
            try {
                session.close();
            } catch (IOException ignored) {
                // 关闭失败可忽略
            }
        }
    }

    /**
     * 当前在线连接数
     */
    public int onlineCount() {
        return SESSION_MAP.size();
    }
}
