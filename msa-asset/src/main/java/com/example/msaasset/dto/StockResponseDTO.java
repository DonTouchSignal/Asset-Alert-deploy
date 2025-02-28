package com.example.msaasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StockResponseDTO {
    private String symbol;       // 종목 코드
    private String koreanName;   // 종목명 (한글) - 변경
    private String englishName;  // 종목명 (영어) - 변경
    private double price;        // 현재가 (Redis에서 조회)
    private double changeRate;   // 등락률 (Redis에서 조회)

    public StockResponseDTO(String symbol, double v, double v1) {
        this.symbol = symbol;
        this.price = v;
        this.changeRate = v1;
    }
}
