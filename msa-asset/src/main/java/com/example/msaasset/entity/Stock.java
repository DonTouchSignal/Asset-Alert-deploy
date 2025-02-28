package com.example.msaasset.entity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "asset")
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String symbol; // 종목 코드 (ex: TSLA, BTC)

    @Column(nullable = true)
    private String koreanName; // 종목명 (한글)

    @Column(nullable = true)
    private String englishName; // 종목명 (영어)

    @Column(nullable = false)
    private String market; // 시장 구분 (ex: NASDAQ, KRX)

    @Column(nullable = true)
    private Integer categoryId; // 종목 분류 ID

    @Column
    private Double changeRate;

    @Column
    private Double price;

    @Column
    private Double volume;

    //@Column(nullable = false)
    //@JsonDeserialize(using = LocalDateTimeDeserializer.class)
    //@JsonSerialize(using = LocalDateTimeSerializer.class)
    //@CreationTimestamp
    private long createdAt = System.currentTimeMillis();


    public Stock(String symbol, String koreanName, String englishName, String market, Integer categoryId) {
        this.symbol = symbol;
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.market = market;
        this.categoryId = categoryId;
        this.createdAt = System.currentTimeMillis();
    }


}



