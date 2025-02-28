package org.example.msasbalert.controller;


import lombok.RequiredArgsConstructor;
import org.example.msasbalert.entity.PriceAlertHistory;
import org.example.msasbalert.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alert")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    // 사용자 알림 내역 조회 API
    @GetMapping
    public ResponseEntity<List<PriceAlertHistory>> getNotifications(
            @RequestHeader(value = "X-Auth-User") String userEmail) {
        List<PriceAlertHistory> notifications = notificationService.getNotificationHistory(userEmail);
        return ResponseEntity.ok(notifications);
    }

    // 알림 삭제 API (개별 알림 삭제)
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<String> deleteNotification(
            @PathVariable Long notificationId,
            @RequestHeader(value = "X-Auth-User", required = false) String userEmail) {

        if (userEmail == null || userEmail.isEmpty()) {
            throw new IllegalArgumentException("❌ 사용자 인증 정보 없음 (X-Auth-User 헤더가 필요합니다)");
        }

        notificationService.deleteNotification(notificationId, userEmail);
        return ResponseEntity.ok("✅ 알림이 삭제되었습니다.");
    }

    // 모든 알림 삭제 API (사용자 알림 전체 삭제)
    @DeleteMapping
    public ResponseEntity<String> deleteAllNotifications(
            @RequestHeader(value = "X-Auth-User", required = false) String userEmail) {

        if (userEmail == null || userEmail.isEmpty()) {
            throw new IllegalArgumentException("❌ 사용자 인증 정보 없음 (X-Auth-User 헤더가 필요합니다)");
        }

        notificationService.deleteAllNotifications(userEmail);
        return ResponseEntity.ok("✅ 모든 알림이 삭제되었습니다.");
    }
}

