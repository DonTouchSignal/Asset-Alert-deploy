package org.example.msasbalert.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.msasbalert.entity.PriceAlertHistory;
import org.example.msasbalert.repository.PriceAlertHistoryRepository;
import org.example.msasbalert.websocket.NotificationWebSocketHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final PriceAlertHistoryRepository priceAlertHistoryRepository;
    private final ObjectMapper objectMapper;
    private final NotificationWebSocketHandler notificationWebSocketHandler;

    @KafkaListener(topics = "target-price-alert", groupId = "notification-group")
    public void consumeTargetPriceAlert(String message) {
        try {
            JsonNode eventData = objectMapper.readTree(message);
            String userEmail = eventData.get("userEmail").asText();
            String symbol = eventData.get("symbol").asText();
            double targetPrice = eventData.get("targetPrice").asDouble();
            double currentPrice = eventData.get("currentPrice").asDouble();
            String condition = eventData.get("condition").asText();
            Date triggeredAt = new Date(eventData.get("timestamp").asLong());

            log.info("📡 목표 가격 도달 이벤트 수신: {} - 현재가 {} (목표가 {} - {})", symbol, currentPrice, targetPrice, condition);

            //  목표 가격 도달 내역을 DB에 저장
            PriceAlertHistory alertHistory = new PriceAlertHistory(userEmail, symbol, targetPrice, currentPrice, condition, triggeredAt);
            priceAlertHistoryRepository.save(alertHistory);
            log.info("✅ 목표 가격 도달 내역 저장 완료: {}", alertHistory);

            //  WebSocket으로 JSON 형식의 사용자 알림 전송
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode jsonMessage = objectMapper.createObjectNode();

            jsonMessage.put("symbol", symbol);
            jsonMessage.put("targetPrice", targetPrice);
            jsonMessage.put("currentPrice", currentPrice);
            jsonMessage.put("condition", condition);
            jsonMessage.put("timestamp", triggeredAt.getTime());

            String jsonString = objectMapper.writeValueAsString(jsonMessage);
            notificationWebSocketHandler.sendMessage(userEmail, jsonString);

            log.info("📡 WebSocket 알림 JSON 전송: {}", jsonString);  // 확인용 로그

        } catch (Exception e) {
            log.error("❌ 목표 가격 이벤트 처리 실패: {}", e.getMessage());
        }
    }

}
