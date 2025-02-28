package com.example.msaasset.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "price_alert")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TargetPrice {

    @EmbeddedId
    private TargetPriceKey id;

    @Column(nullable = false)
    private double targetPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_condition", nullable = false)
    private TargetPriceCondition condition;

    @Column(nullable = false)
    private String status;

}
