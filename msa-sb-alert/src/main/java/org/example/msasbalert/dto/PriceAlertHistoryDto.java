package org.example.msasbalert.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceAlertHistoryDto {
    private String userEmail;
    private String symbol;
    private BigDecimal triggeredPrice;
    private LocalDateTime triggeredAt;
    private String kafkaMessage;
}
