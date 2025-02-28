package com.example.msaasset.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketDataDTO {
    private long stockId;
    private String symbol;
    private Double price;
    private Double high;
    private Double low;
    private Double changeRate;
    private Double tradeAmount;
    private Double volume;
    private String koreanName;
    private String englishName;


    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private long timestamp = System.currentTimeMillis();


    public MarketDataDTO(String symbol, double price, double changeRate) {
        this.symbol = symbol;
        this.price= price;
        this.changeRate=changeRate;
        this.timestamp = System.currentTimeMillis();
    }


    public MarketDataDTO(String symbol, double price, double high, double low, double volume, double changeRate, double tradeAmount) {
        this.symbol = symbol;
        this.price = price;
        this.high = high;
        this.low = low;
        this.volume = volume;
        this.changeRate = changeRate;
        this.tradeAmount = tradeAmount;
        this.timestamp = System.currentTimeMillis();
    }


    public MarketDataDTO(String stockCode, double last, double tvol, double rate, double tamt) {
        this.symbol = stockCode;
        this.price = last;
        this.volume = tvol;
        this.changeRate = rate;
        this.tradeAmount = tamt;
    }


    public MarketDataDTO(String symbol, double price, double high, double low, double changeRate, double volume) {
        this.symbol = symbol;
        this.price = price;
        this.high = high;
        this.low = low;
        this.changeRate = changeRate;
        this.volume = volume;
        this.timestamp = System.currentTimeMillis();
    }
}


