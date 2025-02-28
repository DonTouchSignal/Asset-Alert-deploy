package org.example.msasbalert.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.msasbalert.entity.PriceAlertHistory;
import org.example.msasbalert.repository.PriceAlertHistoryRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PriceAlertHistoryRepository priceAlertHistoryRepository;

    public List<PriceAlertHistory> getNotificationHistory(String userEmail) {
        List<PriceAlertHistory> alertHistoryList = priceAlertHistoryRepository.findByUserEmail(userEmail);
        log.info("📄 알림 내역 조회: [{}] - {}건", userEmail, alertHistoryList.size());
        return alertHistoryList;
    }


    // 개별 알림 삭제 (PriceAlertHistory 기준)
    public void deleteNotification(Long alertId, String userEmail) {
        PriceAlertHistory alertHistory = priceAlertHistoryRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("❌ 해당 알림을 찾을 수 없습니다."));

        if (!alertHistory.getUserEmail().equals(userEmail)) {
            throw new SecurityException("❌ 본인의 알림만 삭제할 수 있습니다.");
        }

        // Redis에서 해당 알림 정보 삭제
        String key = userEmail + ":" + alertHistory.getSymbol();
        redisTemplate.opsForHash().delete("target_prices", key);
        redisTemplate.opsForHash().delete("target_conditions", key);
        redisTemplate.delete("alert_sent:" + key);

        priceAlertHistoryRepository.delete(alertHistory);
        log.info("🗑️ PriceAlertHistory 및 Redis 데이터 삭제 완료: [{}] {}", userEmail, alertId);
    }

    // 사용자의 목표 가격을 도달한 알림만 삭제 (PriceAlertHistory + Redis)
    public void deleteAllNotifications(String userEmail) {
        List<PriceAlertHistory> triggeredAlerts = priceAlertHistoryRepository.findByUserEmailAndTriggeredAtIsNotNull(userEmail);

        for (PriceAlertHistory alert : triggeredAlerts) {
            String key = userEmail + ":" + alert.getSymbol();
            redisTemplate.opsForHash().delete("target_prices", key);
            redisTemplate.opsForHash().delete("target_conditions", key);
            redisTemplate.delete("alert_sent:" + key);
        }

        priceAlertHistoryRepository.deleteAll(triggeredAlerts);
        log.info("🗑️ 사용자의 '목표 가격 도달한' PriceAlertHistory 및 Redis 데이터 삭제 완료: [{}]", userEmail);
    }

}
