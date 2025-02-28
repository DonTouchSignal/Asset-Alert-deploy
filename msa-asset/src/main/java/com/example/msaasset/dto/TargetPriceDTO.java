package com.example.msaasset.dto;

import com.example.msaasset.entity.TargetPriceCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
public class TargetPriceDTO {
    private String userEmail;
    private String symbol;
    private double targetPrice;
    private TargetPriceCondition condition;

    public TargetPriceDTO(String userEmail, String symbol, double targetPrice) {
        this.userEmail=userEmail;
        this.symbol = symbol;
        this.targetPrice = targetPrice;
    }

    public TargetPriceDTO(String userEmail, String symbol, double targetPrice, TargetPriceCondition condition) {
        this.userEmail=userEmail;
        this.symbol = symbol;
        this.targetPrice = targetPrice;
        this.condition = condition;
    }
}
