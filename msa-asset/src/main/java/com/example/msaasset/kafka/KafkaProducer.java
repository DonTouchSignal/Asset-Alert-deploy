package com.example.msaasset.kafka;

import com.example.msaasset.dto.MarketDataDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TOPIC = "stock-price-alert";

    /**
     *  Kafka로 실시간 가격 데이터 전송
     */
    public void sendMarketData(MarketDataDTO marketData) {
        try {
            String message = objectMapper.writeValueAsString(marketData);
            kafkaTemplate.send(TOPIC, marketData.getSymbol(), message);
            log.info("🚀 Kafka 전송 완료 [{}]: {}", marketData.getSymbol(), message);
        } catch (JsonProcessingException e) {
            log.error("❌ Kafka 메시지 변환 실패: {}", e.getMessage());
        }
    }
}
