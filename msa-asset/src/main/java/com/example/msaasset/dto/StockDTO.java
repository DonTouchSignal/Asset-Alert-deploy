package com.example.msaasset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StockDTO {
    private String symbol;
    @JsonProperty("korean_name")
    private String koreanName; // 한글명

    @JsonProperty("english_name")
    private String englishName; // 영어명

    private String market;
    private Integer categoryId;
    private Double price;
    private Double changeRate;




    @Builder
    public StockDTO(String symbol, String koreanName,String englishName, String market, Integer categoryId) {
        this.symbol = symbol;
        this.koreanName = koreanName;
        this.englishName = englishName;
        //this.currentPrice = currentPrice != null ? currentPrice : 0.0;
        this.market = market;
        this.categoryId = categoryId != null ? categoryId : 0;
    }


    public StockDTO(String symbol, String koreanName,String englishName, String market) {
        this.symbol = symbol;
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.market = market;
    }

    public StockDTO(String symbol, String koreanName, String englishName, String market, Integer categoryId, double price, double changeRate) {
        this.symbol = symbol;
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.market = market;
        this.categoryId = categoryId;
        this.price = price;
        this.changeRate = changeRate;
    }
}
