package com.example.msaasset.kafka;

import com.example.msaasset.dto.MarketDataDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumer {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     *  Kafka Consumer: 가격 변동 데이터 수신
     */
    @KafkaListener(topics = "stock-price-alert", groupId = "stock-group")
    public void consumeMarketData(String message) {
        try {
            MarketDataDTO marketData = objectMapper.readValue(message, MarketDataDTO.class);

            //  Redis에 최신 가격 저장 (10분 TTL 적용)
            redisTemplate.opsForValue().set("stock_prices:" + marketData.getSymbol(), String.valueOf(marketData.getPrice()), 10, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set("stock_changes:" + marketData.getSymbol(), String.valueOf(marketData.getChangeRate()), 10, TimeUnit.MINUTES);

            log.info("📡 카프카 수신! Redis 저장 완료 [{}]: 가격 {}, 변동률 {}", marketData.getSymbol(), marketData.getPrice(), marketData.getChangeRate());

        } catch (Exception e) {
            log.error("❌ Kafka 메시지 처리 실패: {}", e.getMessage());
        }
    }
}
