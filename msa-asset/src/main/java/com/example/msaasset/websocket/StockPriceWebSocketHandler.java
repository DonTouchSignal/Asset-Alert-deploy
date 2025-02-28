package com.example.msaasset.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockPriceWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    // 모든 활성 세션 관리 (세션 ID -> 세션 객체)
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 종목별 구독 관리 (종목 심볼 -> 구독 세션 집합)
    private static final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();

    // 세션별 구독 종목 관리 (세션 ID -> 구독 종목 집합)
    private static final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        sessionSubscriptions.put(sessionId, ConcurrentHashMap.newKeySet());
        log.info("✅ 새 WebSocket 연결 성립: {}", sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();

        // 이 세션이 구독한 모든 종목에서 구독 해제
        Set<String> subscribedSymbols = sessionSubscriptions.getOrDefault(sessionId, Set.of());
        for (String symbol : subscribedSymbols) {
            unsubscribeSession(sessionId, symbol);
        }

        // 세션 제거
        sessions.remove(sessionId);
        sessionSubscriptions.remove(sessionId);

        log.info("🚪 WebSocket 연결 종료: {}, 사유: {}", sessionId, status.getReason());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String sessionId = session.getId();
            JsonNode jsonMessage = objectMapper.readTree(message.getPayload());

            String type = jsonMessage.has("type") ? jsonMessage.get("type").asText() : "";
            String symbol = jsonMessage.has("symbol") ? jsonMessage.get("symbol").asText() : "";

            if (symbol.isEmpty()) {
                sendErrorMessage(session, "심볼이 없습니다.");
                return;
            }

            if ("subscribe".equalsIgnoreCase(type)) {
                subscribeSession(sessionId, symbol);
                sendSuccessMessage(session, "구독 성공: " + symbol);
                log.info("📌 구독 등록: {} -> {}", sessionId, symbol);
            } else if ("unsubscribe".equalsIgnoreCase(type)) {
                unsubscribeSession(sessionId, symbol);
                sendSuccessMessage(session, "구독 해제: " + symbol);
                log.info("🔕 구독 해제: {} -> {}", sessionId, symbol);
            } else {
                sendErrorMessage(session, "알 수 없는 메시지 타입: " + type);
                log.warn("⚠️ 알 수 없는 메시지 타입: {}", type);
            }
        } catch (Exception e) {
            log.error("❌ 메시지 처리 중 오류:", e);
            try {
                sendErrorMessage(session, "메시지 처리 실패: " + e.getMessage());
            } catch (Exception ex) {
                log.error("❌ 오류 메시지 전송 실패:", ex);
            }
        }
    }

    // 세션을 특정 종목에 구독 등록
    private void subscribeSession(String sessionId, String symbol) {
        // 종목에 세션 추가
        subscriptions.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        // 세션에 종목 추가
        sessionSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(symbol);
    }

    // 세션의 특정 종목 구독 해제
    private void unsubscribeSession(String sessionId, String symbol) {
        // 종목에서 세션 제거
        if (subscriptions.containsKey(symbol)) {
            subscriptions.get(symbol).remove(sessionId);
            if (subscriptions.get(symbol).isEmpty()) {
                subscriptions.remove(symbol);
            }
        }

        // 세션에서 종목 제거
        if (sessionSubscriptions.containsKey(sessionId)) {
            sessionSubscriptions.get(sessionId).remove(symbol);
        }
    }

    // 성공 메시지 전송
    private void sendSuccessMessage(WebSocketSession session, String message) throws IOException {
        MessageResponse response = new MessageResponse("success", message);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    // 오류 메시지 전송
    private void sendErrorMessage(WebSocketSession session, String message) throws IOException {
        MessageResponse response = new MessageResponse("error", message);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    // 실시간 가격 업데이트 브로드캐스트 - 종목별로 구독자에게만 전송
    public void broadcastStockPriceUpdate(String symbol, double price, double changeRate) {
        if (symbol == null || symbol.isEmpty()) {
            log.warn("⚠️ 빈 심볼로 브로드캐스트 시도");
            return;
        }

        Set<String> subscribers = subscriptions.getOrDefault(symbol, Set.of());
        if (subscribers.isEmpty()) {
            // 구독자가 없는 경우 로그만 남기고 종료
            return;
        }

        try {
            StockPriceUpdate update = new StockPriceUpdate(symbol, price, changeRate);
            String jsonMessage = objectMapper.writeValueAsString(update);
            TextMessage textMessage = new TextMessage(jsonMessage);

            int sentCount = 0;
            for (String sessionId : subscribers) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                        sentCount++;
                    } catch (IOException e) {
                        log.error("❌ 세션 {}에 메시지 전송 실패:", sessionId, e);
                        // 세션이 오류 상태면 구독 해제 고려
                        if (!session.isOpen()) {
                            unsubscribeSession(sessionId, symbol);
                        }
                    }
                } else {
                    // 세션이 없거나 닫힌 경우 구독 해제
                    unsubscribeSession(sessionId, symbol);
                }
            }

            if (sentCount > 0) {
                log.debug("📡 {} 종목 가격 업데이트 브로드캐스트: {}원, {}%, 수신자: {}/{}",
                        symbol, price, changeRate, sentCount, subscribers.size());
            }
        } catch (Exception e) {
            log.error("❌ 브로드캐스트 중 오류 ({}): {}", symbol, e.getMessage());
        }
    }

    // 응답 메시지 클래스
    private static class MessageResponse {
        public String status;
        public String message;

        public MessageResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    // 가격 업데이트 메시지 클래스
    private static class StockPriceUpdate {
        public String type = "price_update";
        public String symbol;
        public double price;
        public double changeRate;
        public long timestamp;

        public StockPriceUpdate(String symbol, double price, double changeRate) {
            this.symbol = symbol;
            this.price = price;
            this.changeRate = changeRate;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void manageWebSocketConnections() {
        boolean domesticMarketOpen = isDomesticMarketOpen();
        boolean usMarketOpen = isUSMarketOpen();

        for (String symbol : subscriptions.keySet()) {
            if (isDomesticStock(symbol)) {
                if (!domesticMarketOpen) {
                    unsubscribeAllSessions(symbol);
                    log.info("⏸ 국내 주식 WebSocket 해제: {}", symbol);
                } else {
                    resubscribeAllSessions(symbol);
                    log.info("✅ 국내 주식 장 개장 - WebSocket 자동 재연결: {}", symbol);
                }
            }
            if (isUSStock(symbol)) {
                if (!usMarketOpen) {
                    unsubscribeAllSessions(symbol);
                    log.info("⏸ 미국 주식 WebSocket 해제: {}", symbol);
                } else {
                    resubscribeAllSessions(symbol);
                    log.info("✅ 미국 주식 장 개장 - WebSocket 자동 재연결: {}", symbol);
                }
            }
        }
    }

    // 기존 구독자를 다시 복원
    private void resubscribeAllSessions(String symbol) {
        Set<String> subscribers = subscriptions.getOrDefault(symbol, Set.of());
        for (String sessionId : subscribers) {
            subscribeSession(sessionId, symbol);
        }
    }


    // 모든 세션에서 특정 종목 구독 해제
    private void unsubscribeAllSessions(String symbol) {
        Set<String> subscribers = subscriptions.getOrDefault(symbol, Set.of());
        for (String sessionId : subscribers) {
            unsubscribeSession(sessionId, symbol);
        }
    }

    private boolean isDomesticMarketOpen() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(15, 30));
    }

    // 미국 주식 장 시간 확인 (한국시간 23:30-06:00)
    private boolean isUSMarketOpen() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(23, 30)) || now.isBefore(LocalTime.of(6, 0));
    }

    // 국내 주식 확인 (6자리 숫자)
    private boolean isDomesticStock(String symbol) {
        return symbol.matches("\\d{6}");
    }

    // 미국 주식 확인 (영문자로 시작, '-' 없음)
    private boolean isUSStock(String symbol) {
        return !symbol.matches("\\d{6}") && !symbol.contains("-");
    }



}