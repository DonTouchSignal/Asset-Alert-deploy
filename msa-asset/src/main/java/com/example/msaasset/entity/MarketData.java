package com.example.msaasset.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Stock stock;

    //private String name;
    /*@JoinColumns({
            @JoinColumn(name = "symbol", referencedColumnName = "symbol"),
            @JoinColumn(name = "categoryId", referencedColumnName = "categoryId")
    }) // FK를 symbol + categoryId 복합키로 설정*/
    //@Column(nullable = false
    //private LocalDateTime timestamp;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal price;


    @Column(nullable = true, precision = 20, scale = 8)
    private BigDecimal high;

    @Column(nullable = true, precision = 20, scale = 8)
    private BigDecimal low;

    @Column(nullable = true, precision = 20, scale = 8)
    private BigDecimal changeRate;

    @Column(nullable = true, precision = 20, scale = 8)
    private BigDecimal tradeAmount;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal volume;




}

