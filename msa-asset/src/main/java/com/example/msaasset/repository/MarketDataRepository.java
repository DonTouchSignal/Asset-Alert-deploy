package com.example.msaasset.repository;

import com.example.msaasset.entity.MarketData;
import com.example.msaasset.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface MarketDataRepository extends JpaRepository<MarketData, Long> {
    Optional<MarketData> findByStock(Stock stock);

    Optional<MarketData> findByStockSymbol(String symbol);

    List<MarketData> findByStock_Id(Long stockId);

    @Modifying
    @Transactional
    @Query("UPDATE MarketData m SET m.price = :price, m.high = :high, m.low = :low, m.volume = :volume WHERE m.stock.symbol = :symbol")
    void updateMarketData(String symbol, double price, double high, double low, double volume);


    @Modifying
    @Transactional
    @Query("UPDATE MarketData m SET m.price = :price, m.high = :high, m.low = :low, m.changeRate = :changeRate, m.tradeAmount = :tradeAmount, m.volume = :volume  WHERE m.stock.symbol = :symbol")
    void updateMarketData(String symbol, double price, double high, double low, double changeRate, double tradeAmount, double volume);
}
