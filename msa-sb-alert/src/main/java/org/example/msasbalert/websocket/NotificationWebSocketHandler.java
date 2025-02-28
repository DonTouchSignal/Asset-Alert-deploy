package org.example.msasbalert.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String email = session.getUri().getQuery().split("=")[1];
        sessions.put(email, session);
        log.info("✅ WebSocket 연결됨: {}", email);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.info("📩 WebSocket 메시지 수신: {}", message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.values().remove(session);
        log.info("❌ WebSocket 연결 종료");
    }

    // 특정 사용자에게 알림 전송
    public void sendMessage(String userEmail, String message) {
        if (sessions.containsKey(userEmail)) {
            try {
                WebSocketSession session = sessions.get(userEmail);
                synchronized (session) { // 동기화로 중복 방지
                    session.sendMessage(new TextMessage(message));
                    log.info("📡 WebSocket 알림 전송 완료: {}", message);
                }
            } catch (IOException e) {
                log.error("❌ WebSocket 메시지 전송 실패", e);
            }
        } else {
            log.warn("⚠️ WebSocket 세션 없음: {}", userEmail);
        }
    }

}
