package org.example.msasbalert.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.antlr.v4.runtime.misc.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Data
@NoArgsConstructor
public class PriceAlertHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @NotNull
    private String userEmail; // 유저 이메일

    private String symbol;

    @Column
    private double targetPrice;

    @Column
    private double triggeredPrice; // 목표 가격 도달 시점의 가격

    @Column(name="alert_condition")
    private String condition;

    @Column(columnDefinition = "TIMESTAMP")
    private Date triggeredAt;


    public PriceAlertHistory(String userEmail, String symbol, double targetPrice, double currentPrice, String condition, Date triggeredAt) {
        this.userEmail=userEmail;
        this.symbol=symbol;
        this.targetPrice=targetPrice;
        this.triggeredPrice=currentPrice;
        this.condition=condition;
        this.triggeredAt=triggeredAt;


    }
}
