package com.example.msaasset.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {
    private final StringRedisTemplate redisTemplate;  // Redis 직접 사용 (설정 필요 없음)

    // 최신 가격 저장 (10분 유지)
    public void saveStockPrice(String symbol, double price, double changeRate) {
        redisTemplate.opsForValue().set("stock_prices:" + symbol, String.valueOf(price), 10, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set("stock_changes:" + symbol, String.valueOf(changeRate), 10, TimeUnit.MINUTES);
        log.info("📌 Redis 저장 완료: [{}] 가격={}, 변동률={}", symbol, price, changeRate);
    }

    // 최신 가격 조회
    public Double getStockPrice(String symbol) {
        String price = redisTemplate.opsForValue().get("stock_prices:" + symbol);
        return price != null ? Double.parseDouble(price) : null;
    }

    // 목표 가격 저장 (TTL 1일)
    public void saveTargetPrice(String email, String symbol, double targetPrice) {
        redisTemplate.opsForHash().put("target_prices:" + email, symbol, String.valueOf(targetPrice));
        redisTemplate.expire("target_prices:" + email, 1, TimeUnit.DAYS);
        log.info("🎯 목표가 설정: [{}] {} → {}", email, symbol, targetPrice);
    }

    // 목표 가격 조회
    public Double getTargetPrice(String email, String symbol) {
        String price = (String) redisTemplate.opsForHash().get("target_prices:" + email, symbol);
        return price != null ? Double.parseDouble(price) : null;
    }

    // 목표 가격 도달 여부 확인
    public boolean isTargetPriceReached(String email, String symbol, double currentPrice) {
        Double targetPrice = getTargetPrice(email, symbol);
        return targetPrice != null && currentPrice >= targetPrice;
    }
}
