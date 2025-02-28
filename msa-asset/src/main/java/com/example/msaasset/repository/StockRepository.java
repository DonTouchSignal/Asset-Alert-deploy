package com.example.msaasset.repository;

import com.example.msaasset.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findBySymbol(String symbol);

    @Query("SELECT s FROM Stock s WHERE " +
            "LOWER(s.koreanName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(s.englishName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(s.symbol) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(s.market) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR CAST(s.categoryId AS string) LIKE CONCAT('%', :keyword, '%')")
    List<Stock> searchStocks(@Param("keyword") String keyword);

    @Query("SELECT s.symbol FROM Stock s")
    List<String> findAllSymbols();

    // 국내 주식 심볼 리스트 조회 (categoryId = 1)
    @Query("SELECT s.symbol FROM Stock s WHERE s.categoryId = 1")
    List<String> findDomesticStockSymbols();

    // 해외 주식 심볼 리스트 조회 (categoryId = 2)
    @Query("SELECT s.symbol FROM Stock s WHERE s.categoryId = 2")
    List<String> findForeignStockSymbols();


    List<Stock> findByCategoryIdIn(List<Integer> list);

    boolean existsBySymbol(String symbol);
}
