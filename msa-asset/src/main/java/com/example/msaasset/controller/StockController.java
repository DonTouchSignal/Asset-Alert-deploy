package com.example.msaasset.controller;

import com.example.msaasset.dto.StockResponseDTO;
import com.example.msaasset.dto.TargetPriceDTO;
import com.example.msaasset.entity.TargetPriceCondition;
import com.example.msaasset.service.StockService;
import com.example.msaasset.websocket.WebSocketSubscriptionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/asset")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") //  CORS 설정 (프론트 허용)
public class StockController {

    private final StockService stockService;
    private final WebSocketSubscriptionManager subscriptionManager;

    // 종목 검색 API (키워드로 검색)
    @GetMapping("/search")
    public List<StockResponseDTO> searchStocks(@RequestParam String keyword) {
        return stockService.searchStocks(keyword);
    }

    // 종목 상세 조회 API
    @GetMapping("/{symbol}")
    public StockResponseDTO getStockDetail(
            @RequestHeader(value = "X-Auth-User", required = false) String userEmail,
            @PathVariable String symbol) {
        return stockService.getStockDetail(symbol);
    }

    // 변동률 높은 상위 5개 종목 조회 API
    // 음...
    @GetMapping("/top-movers")
    public List<StockResponseDTO> getTopMovers() {
        List<StockResponseDTO> stockList = stockService.getTopStocksFromRedis("top_movers_stocks");
        List<StockResponseDTO> cryptoList = stockService.getTopStocksFromRedis("top_movers_cryptos");

        List<StockResponseDTO> finalList = new ArrayList<>();
        finalList.addAll(stockList);
        finalList.addAll(cryptoList);

        return finalList; // 🔥 반드시 List<StockResponseDTO> 형태로 반환
    }


    // 관심 종목 추가 API
    @PostMapping("/favorite")
    public void addFavoriteStock(
            @RequestHeader(value = "X-Auth-User") String userEmail,
            @RequestParam String symbol) {
        stockService.addFavoriteStock(userEmail, symbol);
    }

    // 관심 종목 삭제 API
    @DeleteMapping("/favorite")
    public void removeFavoriteStock(
            @RequestHeader(value = "X-Auth-User") String userEmail,
            @RequestParam String symbol) {
        stockService.removeFavoriteStock(userEmail, symbol);
    }

    // 관심 종목 목록 조회 API
    @GetMapping("/favorite")
    public Set<String> getFavoriteStocks(@RequestHeader(value = "X-Auth-User") String userEmail) {
        return stockService.getFavoriteStocks(userEmail);
    }

    // 목표 가격 설정 API
    @PostMapping("/target-price")
    public void setTargetPrice(
            @RequestHeader(value = "X-Auth-User") String userEmail,
            @RequestParam String symbol,
            @RequestParam double targetPrice,
            @RequestParam TargetPriceCondition condition) {
        stockService.setTargetPrice(userEmail, symbol, targetPrice, condition);
    }

    //목표가격조회
    @GetMapping("/target-prices")
    public List<TargetPriceDTO> getTargetPrices(@RequestHeader(value = "X-Auth-User") String userEmail) {
        return stockService.getTargetPrices(userEmail);
    }


    // 목표 가격 삭제 API
    @DeleteMapping("/target-price")
    public void removeTargetPrice(
            @RequestHeader(value = "X-Auth-User") String userEmail,
            @RequestParam String symbol) {
        stockService.removeTargetPrice(userEmail, symbol);
    }

    @GetMapping("/unsubscribe/{symbol}")
    public ResponseEntity<Void> unsubscribeFromSymbol(@PathVariable String symbol) {
        subscriptionManager.unsubscribeFromSymbol(symbol);
        return ResponseEntity.ok().build();
    }


}