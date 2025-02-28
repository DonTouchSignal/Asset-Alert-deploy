package org.example.msasbalert.repository;

import org.example.msasbalert.entity.PriceAlertHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceAlertHistoryRepository extends JpaRepository<PriceAlertHistory, Long> {
    void deleteByUserEmail(String userEmail);

    List<PriceAlertHistory> findByUserEmail(String userEmail);

    List<PriceAlertHistory> findByUserEmailAndTriggeredAtIsNotNull(String userEmail);
}
