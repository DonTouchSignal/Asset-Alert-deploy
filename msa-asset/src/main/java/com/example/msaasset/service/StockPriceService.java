package com.example.msaasset.service;

import com.example.msaasset.dto.MarketDataDTO;
import com.example.msaasset.websocket.StockPriceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 주식 가격 서비스 - Redis에 저장된 실시간 가격 정보를 웹소켓으로 브로드캐스팅하는 기능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final StringRedisTemplate redisTemplate;
    private final StockPriceWebSocketHandler webSocketHandler;


    //메서드 수
    @Scheduled(fixedRate = 3000)
    public void broadcastPriceUpdates() {
        try {
            Set<String> keys = redisTemplate.keys("stock_prices:*");
            if (keys.isEmpty()) {
                return;
            }

            boolean domesticMarketOpen = isDomesticMarketOpen();
            boolean usMarketOpen = isUSMarketOpen();

            for (String key : keys) {
                try {
                    String symbol = key.substring("stock_prices:".length());

                    // 장이 닫힌 주식은 전송하지 않음
                    if (isDomesticStock(symbol) && !domesticMarketOpen) {
                        log.info("⏸ 국내 주식 장 종료 - WebSocket 전송 중단: {}", symbol);
                        continue;
                    }
                    if (isUSStock(symbol) && !usMarketOpen) {
                        log.info("⏸ 미국 주식 장 종료 - WebSocket 전송 중단: {}", symbol);
                        continue;
                    }

                    String priceStr = redisTemplate.opsForValue().get(key);
                    String changeRateStr = redisTemplate.opsForValue().get("stock_changes:" + symbol);

                    if (priceStr != null && changeRateStr != null) {
                        double price = Double.parseDouble(priceStr);
                        double changeRate = Double.parseDouble(changeRateStr);

                        String lastSentKey = "last_sent:" + symbol;
                        String lastSentData = redisTemplate.opsForValue().get(lastSentKey);
                        String currentData = price + ":" + changeRate;

                        if (lastSentData == null || !lastSentData.equals(currentData)) {
                            webSocketHandler.broadcastStockPriceUpdate(symbol, price, changeRate);
                            redisTemplate.opsForValue().set(lastSentKey, currentData, 10, TimeUnit.MINUTES);
                            log.info("📡 WebSocket 브로드캐스트: {} → {}, {}%", symbol, price, changeRate);
                        }
                    }
                } catch (Exception e) {
                    log.error("❌ 종목 {} 가격 브로드캐스팅 실패: {}", key, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("❌ 가격 브로드캐스팅 실패: {}", e.getMessage());
        }
    }



    // 국내 주식 장 시간 확인 (9:00-15:30)
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