package com.example.msaasset.service;

import com.example.msaasset.client.KisClient;
import com.example.msaasset.client.UpbitClient;
import com.example.msaasset.dto.*;
import com.example.msaasset.entity.*;
import com.example.msaasset.repository.StockRepository;
import com.example.msaasset.repository.TargetPriceRepository;
import com.example.msaasset.repository.WatchListRepository;
import com.example.msaasset.websocket.StockPriceWebSocketHandler;
import com.example.msaasset.websocket.WebSocketSubscriptionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final KafkaTemplate<String,String> kafkaTemplate;
    private final UpbitClient upbitClient;
    private final KisClient kisClient;
    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WatchListRepository watchListRepository;
    private final TargetPriceRepository targetPriceRepository;
    private final StringRedisTemplate redisTemplate;
    private final StockPriceWebSocketHandler webSocketHandler;
    private final WebSocketSubscriptionManager subscriptionManager;


    @PostConstruct
    public void initializeStockDataFromUpbit() {
        try {
            log.info("Upbit 종목 데이터 초기화 시작");

            List<StockDTO> stockDTOs = upbitClient.fetchStockList();
            stockDTOs.forEach(stock ->
                    log.info("📌 종목: {}, 한국어: {}, 영어: {}",
                            stock.getSymbol(), stock.getKoreanName(), stock.getEnglishName()));

            List<String> existingSymbols = stockRepository.findAllSymbols();

            Map<String, Stock> newStocks = stockDTOs.stream()
                    .filter(dto -> !existingSymbols.contains(dto.getSymbol()))
                    .collect(Collectors.toMap(
                            StockDTO::getSymbol,
                            dto -> new Stock(
                                    dto.getSymbol(),
                                    dto.getKoreanName() != null ? dto.getKoreanName() : "미확인",
                                    dto.getEnglishName() != null ? dto.getEnglishName() : "Unknown",
                                    "upbit",
                                    0
                            ),
                            (oldValue, newValue) -> oldValue
                    ));

            if (!newStocks.isEmpty()) {
                stockRepository.saveAll(newStocks.values());
                log.info("✅ {}개의 새로운 종목 추가 완료", newStocks.size());
            } else {
                log.info("🔄 추가된 종목 없음 (중복)");
            }
        } catch (Exception e) {
            log.error("❌ Upbit 데이터 초기화 실패: ", e);
        }
    }


    /*public List<String> getAllSymbols() {
        return stockRepository.findAll().stream()
                .map(Stock::getSymbol) // Stock 엔티티의 getSymbol 메서드를 참조
                .collect(Collectors.toList());
    }*/





    /// 리뉴얼
    ///
    ///
    private double safeParseDouble(String value) {
        try {
            return (value != null && !value.equalsIgnoreCase("null") && !value.isEmpty()) ? Double.parseDouble(value) : 0.0;
        } catch (NumberFormatException e) {
            log.warn("🚨 숫자 변환 오류: {}", value);
            return 0.0;
        }
    }



    //종목검색 리뉴얼
    public List<StockResponseDTO> searchStocks(String keyword) {
        List<Stock> stocks = stockRepository.searchStocks(keyword);

        return stocks.stream().map(stock -> {
            // 최신 가격 및 변동률 Redis에서 가져오기
            String price = redisTemplate.opsForValue().get("stock_prices:" + stock.getSymbol());
            String changeRate = redisTemplate.opsForValue().get("stock_changes:" + stock.getSymbol());

            return new StockResponseDTO(
                    stock.getSymbol(),
                    stock.getKoreanName(),  //
                    stock.getEnglishName(), //
                    safeParseDouble(price),
                    safeParseDouble(changeRate)
            );
        }).collect(Collectors.toList());
    }


    // 목표가격 체크+카프카
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void checkTargetPricesAndNotify() throws JsonProcessingException {
        List<TargetPrice> targetPrices = targetPriceRepository.findAll();

        for (TargetPrice target : targetPrices) {
            String symbol = target.getId().getSymbol();

            // Redis에서 현재 가격 조회
            String currentPriceStr = redisTemplate.opsForValue().get("stock_prices:" + symbol);
            if (currentPriceStr == null) continue;

            double currentPrice = Double.parseDouble(currentPriceStr);

            // 목표 가격 조건 체크
            if ((target.getCondition() == TargetPriceCondition.ABOVE && currentPrice >= target.getTargetPrice()) ||
                    (target.getCondition() == TargetPriceCondition.BELOW && currentPrice <= target.getTargetPrice())) {

                // Kafka 이벤트 전송
                Map<String, Object> message = new HashMap<>();
                message.put("userEmail", target.getId().getUserEmail());
                message.put("symbol", symbol);
                message.put("targetPrice", target.getTargetPrice());
                message.put("currentPrice", currentPrice);
                message.put("condition", target.getCondition().name());
                message.put("timestamp", System.currentTimeMillis());

                kafkaTemplate.send("target-price-alert", objectMapper.writeValueAsString(message));
                log.info("🎯 Kafka 목표 가격 도달 알림 전송: {} - 현재가: {}", symbol, currentPrice);

                // 목표 달성 후 삭제
                targetPriceRepository.delete(target);
            }
        }
    }




    //  목표 가격 설정 (이상/이하 옵션 포함)
    @Transactional
    public void setTargetPrice(String userEmail, String symbol, double targetPrice, TargetPriceCondition condition) {
        if (userEmail == null || userEmail.isEmpty()) {
            throw new IllegalArgumentException("❌ 사용자 인증 정보 없음");
        }

        if (condition == null) {
            throw new IllegalArgumentException("❌ 목표 가격 설정 오류: 'ABOVE' 또는 'BELOW' 중 하나여야 합니다.");
        }

        // 실제 존재하는 종목인지 확인
        boolean isStockExist = stockRepository.existsBySymbol(symbol);
        if (!isStockExist) {
            throw new IllegalArgumentException("❌ 존재하지 않는 종목입니다: " + symbol);
        }

        // DB에 목표 가격 저장
        TargetPriceKey targetPriceKey = new TargetPriceKey(userEmail, symbol);
        TargetPrice target = new TargetPrice(targetPriceKey, targetPrice, condition, "ACTIVE");
        targetPriceRepository.save(target);

        // Redis에 목표 가격 저장 (빠른 비교를 위해)
        redisTemplate.opsForHash().put("target_prices", userEmail + ":" + symbol, String.valueOf(targetPrice));
        redisTemplate.opsForHash().put("target_conditions", userEmail + ":" + symbol, condition.name());
        redisTemplate.expire("target_prices", 7, TimeUnit.DAYS);
        redisTemplate.expire("target_conditions", 7, TimeUnit.DAYS);

        log.info("🎯 목표 가격 설정: [{}] {} {} → {}", userEmail, symbol, condition.name(), targetPrice);
    }







    // 목표가격 조회 리뉴얼
    public Double getTargetPrice(String email, String symbol) {
        String price = (String) redisTemplate.opsForHash().get("target_prices", email + ":" + symbol);
        return price != null ? Double.parseDouble(price) : null;
    }

    // 관심종목 목록 조회
    public Set<String> getFavoriteStocks(@RequestHeader(value = "X-Auth-User", required = false) String userEmail) {
        if (userEmail == null || userEmail.isEmpty()) {
            throw new IllegalArgumentException("❌ 사용자 인증 정보 없음 (X-Auth-User 헤더가 필요합니다)");
        }

        // DB에서 관심 종목 조회
        List<WatchList> watchLists = watchListRepository.findByIdUserEmail(userEmail);
        Set<String> favoriteStocks = watchLists.stream()
                .map(WatchList::getSymbol)
                .collect(Collectors.toSet());

        log.info("📦 DB에서 관심 종목 조회: {}", favoriteStocks);
        return favoriteStocks;
    }



    //  특정 종목의 현재가 & 변동률 조회- 종목 상세정보 리뉴얼
    public StockResponseDTO getStockDetail(String symbol) {
        // 종목 상세 페이지 접근 시 해당 종목 구독
        subscriptionManager.subscribeToSymbol(symbol);

        // 기존 로직
        String price = redisTemplate.opsForValue().get("stock_prices:" + symbol);
        String changeRate = redisTemplate.opsForValue().get("stock_changes:" + symbol);

        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("해당 종목을 찾을 수 없습니다: " + symbol));

        return new StockResponseDTO(
                stock.getSymbol(),
                stock.getKoreanName(),
                stock.getEnglishName(),
                price != null ? Double.parseDouble(price) : 0.0,
                changeRate != null ? Double.parseDouble(changeRate) : 0.0
        );
    }


    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void updateTopMoversInRedis() {
        log.info("🔄 변동률 상위 종목(급상승 + 급하락) 업데이트 시작");

        List<Stock> allStocks = stockRepository.findAll();
        Map<String, Double> stockChangeRates = new HashMap<>();
        Map<String, Double> cryptoChangeRates = new HashMap<>();

        for (Stock stock : allStocks) {
            String changeRateStr = redisTemplate.opsForValue().get("stock_changes:" + stock.getSymbol());
            if (changeRateStr != null) {
                double changeRate = Double.parseDouble(changeRateStr);

                // 가격 저장
                redisTemplate.opsForValue().set("stock_prices:" + stock.getSymbol(), String.valueOf(stock.getPrice()));

                // 이름 저장 (null 방지)
                String koreanName = redisTemplate.opsForValue().get("stock_korean_names:" + stock.getSymbol());
                String englishName = redisTemplate.opsForValue().get("stock_english_names:" + stock.getSymbol());

                if (koreanName == null) {
                    koreanName = (stock.getKoreanName() != null) ? stock.getKoreanName() : "";
                    redisTemplate.opsForValue().set("stock_korean_names:" + stock.getSymbol(), koreanName);
                }

                if (englishName == null) {
                    englishName = (stock.getEnglishName() != null) ? stock.getEnglishName() : "";
                    redisTemplate.opsForValue().set("stock_english_names:" + stock.getSymbol(), englishName);
                }

                // 급등/급락 정렬
                if (stock.getSymbol().contains("-")) {
                    cryptoChangeRates.put(stock.getSymbol(), Math.abs(changeRate)); // 암호화폐 변동률도 절대값 적용
                } else {
                    stockChangeRates.put(stock.getSymbol(), Math.abs(changeRate)); // 기존대로 주식도 절대값 적용
                }

            }
        }

        // **급등/급락 주식 상위 3개**
        List<Map.Entry<String, Double>> topStocks = stockChangeRates.entrySet()
                .stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(3)
                .toList();

        // **급등/급락 암호화폐 상위 3개**
        List<Map.Entry<String, Double>> topCryptos = cryptoChangeRates.entrySet()
                .stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(3)
                .toList();

        // 기존 Redis 데이터 삭제 후 업데이트
        redisTemplate.delete("top_movers_stocks");
        redisTemplate.delete("top_movers_cryptos");

        for (var stock : topStocks) {
            redisTemplate.opsForZSet().add("top_movers_stocks", stock.getKey(), stock.getValue());
        }
        for (var crypto : topCryptos) {
            redisTemplate.opsForZSet().add("top_movers_cryptos", crypto.getKey(), crypto.getValue());
        }

        log.info("✅ 급등/급락 상위 3개 (주식 + 암호화폐) Redis 업데이트 완료");
    }





    public List<StockResponseDTO> getTopStocksFromRedis(String redisKey) {
        Set<String> topSymbols = redisTemplate.opsForZSet().reverseRange(redisKey, 0, 2);
        List<StockResponseDTO> stockList = new ArrayList<>();

        if (topSymbols == null || topSymbols.isEmpty()) {
            log.warn("⚠️ Redis에서 `{}` 데이터 없음. 업데이트 필요", redisKey);
            return new ArrayList<>();
        }

        for (String symbol : topSymbols) {
            String price = redisTemplate.opsForValue().get("stock_prices:" + symbol);
            String changeRate = redisTemplate.opsForValue().get("stock_changes:" + symbol);
            String koreanName = redisTemplate.opsForValue().get("stock_korean_names:" + symbol);
            String englishName = redisTemplate.opsForValue().get("stock_english_names:" + symbol);

            if (price == null || changeRate == null) {
                log.warn("⚠️ Redis에서 데이터 없음: {} (이름: {}, 가격: {}, 변동률: {})", symbol, koreanName, price, changeRate);
                continue;
            }

            // 한글 이름이 있으면 한글, 없으면 영어 이름, 둘 다 없으면 symbol 사용
            String displayName = (koreanName != null && !koreanName.isEmpty()) ? koreanName
                    : (englishName != null && !englishName.isEmpty()) ? englishName
                    : symbol;

            stockList.add(new StockResponseDTO(
                    symbol,
                    displayName,
                    null,
                    Double.parseDouble(price),
                    Double.parseDouble(changeRate)
            ));
        }
        return stockList;
    }









    // 목표 가격 삭제 리뉴얼
    public void removeTargetPrice(String email, String symbol) {
        redisTemplate.opsForHash().delete("target_prices", email + ":" + symbol);
        redisTemplate.opsForHash().delete("target_conditions", email + ":" + symbol);
        targetPriceRepository.deleteById(new TargetPriceKey(email, symbol));
        log.info("🗑️ 목표 가격 삭제: [{}] {}", email, symbol);
    }


    //  관심 종목 삭제 (추가 기능)
    /*public void removeFavoriteStock(String email, String symbol) {
        //DB쪽도 추가해야함
        redisTemplate.opsForSet().remove("favorite_stocks:" + email, symbol);
        redisTemplate.opsForHash().delete("target_prices:" + email, symbol);
        redisTemplate.opsForHash().delete("target_conditions:" + email, symbol);
        log.info("🗑️ 관심 종목 및 목표 가격 삭제: [{}] {}", email, symbol);
    }
     */


    // redis->db 저장....필요한가
    @Scheduled(fixedRate = 30000) // 5분마다 실행
    public void saveRedisDataToDatabase() {
        List<Stock> assets = stockRepository.findAll();

        for (Stock asset : assets) {
            try {
                // Redis에서 가격 및 변동률 가져오기
                String priceStr = redisTemplate.opsForValue().get("stock_prices:" + asset.getSymbol());
                String changeRateStr = redisTemplate.opsForValue().get("stock_changes:" + asset.getSymbol());

                double price = safeParseDouble(priceStr);
                double changeRate = safeParseDouble(changeRateStr);

                String volumeStr = redisTemplate.opsForValue().get("stock_volumes:" + asset.getSymbol());

                if (priceStr != null && changeRateStr != null) {
                    asset.setPrice(Double.parseDouble(priceStr));
                    asset.setChangeRate(Double.parseDouble(changeRateStr));
                    asset.setVolume(volumeStr != null ? Double.parseDouble(volumeStr) : null);

                    stockRepository.save(asset);
                    log.info("✅ DB 가격 업데이트 완료: [{}] {} → {}원 (변동률: {}%)", asset.getId(), asset.getSymbol(), asset.getPrice(), asset.getChangeRate());
                } else {
                    log.warn("⚠️ Redis에 가격 데이터 없음: {}", asset.getSymbol());
                }
            } catch (Exception e) {
                log.error("❌ 가격 업데이트 실패: {}", asset.getSymbol(), e);
            }
        }
    }



    @Scheduled(fixedRate = 300000)
    public void loadTargetPricesToRedis() {
        List<TargetPrice> targetPrices = targetPriceRepository.findAll();

        for (TargetPrice target : targetPrices) {
            redisTemplate.opsForHash().put("target_prices", target.getId().getUserEmail() + ":" + target.getId().getSymbol(), String.valueOf(target.getTargetPrice()));
            redisTemplate.opsForHash().put("target_conditions", target.getId().getUserEmail() + ":" + target.getId().getSymbol(), target.getCondition().name());
        }

        log.info("✅ 목표 가격 DB → Redis 동기화 완료 ({} 개)", targetPrices.size());
    }



    @Transactional
    public void addFavoriteStock(String userEmail, String symbol) {
        WatchListKey watchListKey = new WatchListKey(userEmail, symbol);

        if (watchListRepository.existsById(watchListKey)) {
            throw new IllegalStateException("❌ 이미 관심 종목에 등록된 항목입니다.");
        }

        WatchList watchList = new WatchList(watchListKey);
        watchListRepository.save(watchList);

        log.info("⭐ 관심 종목 추가: [{}] {}", userEmail, symbol);
    }


    @Transactional
    public void removeFavoriteStock(String userEmail, String symbol) {
        WatchListKey watchListKey = new WatchListKey(userEmail, symbol);
        if (watchListRepository.existsById(watchListKey)) {
            watchListRepository.deleteById(watchListKey);
            log.info("🗑️ 관심 종목 삭제 완료: [{}] {}", userEmail, symbol);
        } else {
            log.warn("⚠️ 삭제할 관심 종목이 존재하지 않습니다: [{}] {}", userEmail, symbol);
        }
        redisTemplate.opsForSet().remove("favorite_stocks:" + userEmail, symbol);
        redisTemplate.opsForHash().delete("target_prices", userEmail + ":" + symbol);
        redisTemplate.opsForHash().delete("target_conditions", userEmail + ":" + symbol);
        targetPriceRepository.deleteById(new TargetPriceKey(userEmail, symbol));
        log.info("🗑️ 관심 종목 및 목표 가격 삭제: [{}] {}", userEmail, symbol);
    }


    @Scheduled(fixedRate = 30000) // 30초마다 실행
    public void updateCryptoDataFromRestApi() {
        log.info("🔄 암호화폐 데이터 REST API 업데이트 시작");

        // 모든 암호화폐 심볼 가져오기 (KRW 마켓만)
        List<String> cryptoSymbols = stockRepository.findAll().stream()
                .filter(stock -> stock.getSymbol().contains("-"))
                .map(Stock::getSymbol)
                .collect(Collectors.toList());

        // 50개씩 그룹화하여 API 호출
        for (int i = 0; i < cryptoSymbols.size(); i += 50) {
            List<String> batch = cryptoSymbols.subList(i, Math.min(i + 50, cryptoSymbols.size()));
            String markets = String.join(",", batch);

            try {
                // 업비트 REST API 호출
                upbitClient.fetchBatchTickerData(markets);
                Thread.sleep(500); // API 제한 방지
            } catch (Exception e) {
                log.error("❌ 암호화폐 데이터 업데이트 실패: {}", e.getMessage());
            }
        }

        log.info("✅ 암호화폐 데이터 REST API 업데이트 완료");
    }


    @Scheduled(fixedRate = 10000) // 5초마다 가격 업데이트하고 웹소켓
    public void updateStockPrices() {
        // 카테고리 ID가 1(국내) 또는 2(해외)인 주식만 조회
        List<Stock> stocks = stockRepository.findByCategoryIdIn(Arrays.asList(1, 2));

        for (Stock stock : stocks) {
            String priceStr = redisTemplate.opsForValue().get("stock_prices:" + stock.getSymbol());
            String changeRateStr = redisTemplate.opsForValue().get("stock_changes:" + stock.getSymbol());

            // Redis에 데이터가 없으면 REST API로 데이터 가져오기
            if (priceStr == null || changeRateStr == null) {
                try {
                    // 국내/해외 구분하여 데이터 가져오기
                    MarketDataDTO marketData;
                    if (stock.getCategoryId() == 1) { // 국내 주식
                        marketData = kisClient.getDomesticStockPrice(stock.getSymbol());
                    } else { // 해외 주식
                        marketData = kisClient.getForeignStockPrice(stock.getSymbol());
                    }

                    if (marketData != null && marketData.getPrice() != null && marketData.getChangeRate() != null) {
                        updateStockPriceInRedis(stock.getSymbol(), marketData);
                        priceStr = String.valueOf(marketData.getPrice());
                        changeRateStr = String.valueOf(marketData.getChangeRate());
                    }
                } catch (Exception e) {
                    log.warn("⚠️ REST API 데이터 조회 실패: {}", stock.getSymbol(), e);
                }
            }

            if (priceStr != null && changeRateStr != null) {
                double price = Double.parseDouble(priceStr);
                double changeRate = Double.parseDouble(changeRateStr);

                //  실시간 업데이트 알림
                webSocketHandler.broadcastStockPriceUpdate(stock.getSymbol(), price, changeRate);
            }
        }
    }


    // 시장 시간에 따라 REST API로 주식 데이터 가져오기
    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void fetchStockDataFromRestApi() {
        // 웹소켓이 연결되지 않았거나 장중이 아닐 때만 REST API 사용
        boolean isDomesticMarketClosed = !kisClient.isDomesticMarketOpen();
        boolean isUSMarketClosed = !kisClient.isUSMarketOpen();

        log.info("📊 REST API 데이터 갱신 시작 - 국내시장 마감: {}, 미국시장 마감: {}",
                isDomesticMarketClosed, isUSMarketClosed);

        // 국내 주식 처리
        if (isDomesticMarketClosed) {
            List<String> domesticStocks = stockRepository.findDomesticStockSymbols();
            for (String symbol : domesticStocks) {
                try {
                    MarketDataDTO marketData = kisClient.getDomesticStockPrice(symbol);
                    updateStockPriceInRedis(symbol, marketData);
                    Thread.sleep(200);
                } catch (Exception e) {
                    log.error("❌ 국내 주식 데이터 조회 실패: {}", symbol, e);
                }
            }
        }

        // 해외 주식 처리
        if (isUSMarketClosed) {
            List<String> foreignStocks = stockRepository.findForeignStockSymbols();
            for (String symbol : foreignStocks) {
                try {
                    MarketDataDTO marketData = kisClient.getForeignStockPrice(symbol);
                    updateStockPriceInRedis(symbol, marketData);
                    Thread.sleep(300);
                } catch (Exception e) {
                    log.error("❌ 해외 주식 데이터 조회 실패: {}", symbol, e);
                }
            }
        }

        log.info("✅ REST API 데이터 갱신 완료");
    }

    // Redis에 주식 가격 데이터 업데이트
    private void updateStockPriceInRedis(String symbol, MarketDataDTO marketData) {
        if (marketData != null) {
            // 가격 저장 (항상 값이 있어야 함)
            double price = marketData.getPrice() != null ? marketData.getPrice() : 0.0;
            redisTemplate.opsForValue().set("stock_prices:" + symbol,
                    String.valueOf(price), 30, TimeUnit.MINUTES); // TTL 증가 (10분 -> 30분)

            // 변동률 저장 (null 체크 추가)
            double changeRate = marketData.getChangeRate() != null ? marketData.getChangeRate() : 0.0;
            redisTemplate.opsForValue().set("stock_changes:" + symbol,
                    String.valueOf(changeRate), 30, TimeUnit.MINUTES); // TTL 증가, 가격과 동일하게

            // 거래량 정보가 있다면 저장
            if (marketData.getVolume() != null) {
                redisTemplate.opsForValue().set("stock_volumes:" + symbol,
                        String.valueOf(marketData.getVolume()), 30, TimeUnit.MINUTES);
            }

            log.info("📡 Redis 저장 완료: {} -> 가격: {}, 변동률: {}",
                    symbol, price, changeRate);
        }
    }


    // 목표 가격 조회
    @Transactional(readOnly = true)
    public List<TargetPriceDTO> getTargetPrices(String userEmail) {
        if (userEmail == null || userEmail.isEmpty()) {
            throw new IllegalArgumentException("❌ 사용자 인증 정보 없음");
        }

        List<TargetPrice> targetPrices = targetPriceRepository.findByIdUserEmail(userEmail);
        return targetPrices.stream()
                .map(t -> new TargetPriceDTO(t.getId().getUserEmail(), t.getId().getSymbol(), t.getTargetPrice(), t.getCondition()))
                .collect(Collectors.toList());
    }



}