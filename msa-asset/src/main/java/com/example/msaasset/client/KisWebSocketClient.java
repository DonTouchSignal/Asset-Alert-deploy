package com.example.msaasset.client;

import com.example.msaasset.dto.MarketDataDTO;
import com.example.msaasset.entity.TargetPriceCondition;
import com.example.msaasset.kafka.KafkaProducer;
import com.example.msaasset.repository.StockRepository;
import com.example.msaasset.service.StockService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class KisWebSocketClient extends WebSocketClient {
    private static final String REAL_URL = "ws://ops.koreainvestment.com:21000";
    private final KafkaProducer kafkaProducerClient;
    private final KisWebSocketService kisWebSocketService;
    private final StockService stockService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Queue<String> subscriptionQueue = new LinkedList<>();
    private final StockRepository stockRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    public KisWebSocketClient(KafkaProducer kafkaProducerClient, KisWebSocketService kisWebSocketService, StockService stockService, StockRepository stockRepository, RedisTemplate<String,Object> redisTemplate, KafkaTemplate<String, String> kafkaTemplate) throws Exception {
        super(new URI(REAL_URL));
        this.kafkaProducerClient = kafkaProducerClient;
        this.kisWebSocketService = kisWebSocketService;
        this.stockService = stockService;
        this.stockRepository = stockRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        connectBlocking(); // WebSocket 연결
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("✅ WebSocket 연결 성공!");
        String approvalKey = kisWebSocketService.getApprovalKey(); // API 접근 키 발급

        //  DB에서 직접 종목 조회
        List<String> domesticStocks = stockRepository.findDomesticStockSymbols();
        List<String> foreignStocks = stockRepository.findForeignStockSymbols();

        log.info("📌 국내 주식 개수: {}", domesticStocks.size());
        log.info("📌 해외 주식 개수: {}", foreignStocks.size());

        startSubscriptionCycle(approvalKey, domesticStocks, "H0STCNT0"); // 국내 주식 (0.5초 주기)
        startSubscriptionCycle(approvalKey, foreignStocks, "HDFSCNT0"); // 해외 주식 (0.5초 주기)
    }


    private final Set<String> processedDomesticStocks = new HashSet<>();
    private final Set<String> processedForeignStocks = new HashSet<>();

    // KisWebSocketClient.java의 startSubscriptionCycle 메서드 수정
    private void startSubscriptionCycle(String approvalKey, List<String> stockList, String trId) {
        // 배치 크기를 더 작게 조정
        int batchSize = 3; // 한 번에 처리할 종목 수

        // 종목 리스트를 batchSize 크기의 배치로 분할
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < stockList.size(); i += batchSize) {
            batches.add(new ArrayList<>(stockList.subList(i, Math.min(i + batchSize, stockList.size()))));
        }

        // 현재 구독 중인 종목 추적
        Set<String> currentSubscriptions = ConcurrentHashMap.newKeySet();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        AtomicInteger batchIndex = new AtomicInteger(0);

        scheduler.scheduleAtFixedRate(() -> {
            int currentIndex = batchIndex.getAndIncrement() % batches.size();
            List<String> currentBatch = batches.get(currentIndex);

            log.info("🔄 [{}] 배치 {}/{} 주식 구독 순환 시작", trId, currentIndex + 1, batches.size());

            // 이전 배치 구독 해제
            for (String symbol : new ArrayList<>(currentSubscriptions)) {
                unsubscribeStock(approvalKey, trId, symbol);
                currentSubscriptions.remove(symbol);

                // 각 해제 요청 사이에 짧은 지연 추가
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 해제 후 약간의 지연 추가
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 새 배치 구독
            for (String symbol : currentBatch) {
                if (!currentSubscriptions.contains(symbol)) {
                    subscribeStock(approvalKey, trId, symbol);
                    currentSubscriptions.add(symbol);

                    // 각 구독 요청 사이에 짧은 지연 추가
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            log.info("🔄 [{}] 배치 구독 순환 완료", trId);
        }, 0, 1500, TimeUnit.MILLISECONDS); // 2초마다 실행 (시간 간격 늘림)
    }


    void subscribeStock(String approvalKey, String trId, String symbol) {
        // 해외 주식이면 DNAS 접두어 추가
        String formattedSymbol = "HDFSCNT0".equals(trId) ? "DNAS" + symbol : symbol;

        String requestJson = String.format(
                "{\"header\": {\"approval_key\": \"%s\", \"custtype\": \"P\", \"tr_type\": \"1\", \"content-type\": \"utf-8\"}, \"body\": {\"input\": {\"tr_id\": \"%s\", \"tr_key\": \"%s\"}}}",
                approvalKey, trId, formattedSymbol
        );
        send(requestJson);
        log.info("📩 [{}] 실시간 구독 요청 완료 (tr_key: {})", symbol, formattedSymbol);
    }


    private void unsubscribeStock(String approvalKey, String trId, String symbol) {
        String unsubscribeJson = String.format(
                "{\"header\": {\"approval_key\": \"%s\", \"custtype\": \"P\", \"tr_type\": \"2\", \"content-type\": \"utf-8\"}, \"body\": {\"input\": {\"tr_id\": \"%s\", \"tr_key\": \"%s\"}}}",
                approvalKey, trId, symbol
        );
        send(unsubscribeJson);
        log.info("🚫 [{}] 기존 구독 해제 요청", symbol);
    }

    @Override
    public void onMessage(String message) {
        log.info("📩 수신된 WebSocket 메시지: {}", message);
        log.info("📩 메시지 형식: startsWith '{'? {}, contains '|'? {}",
                message.trim().startsWith("{"),
                message.contains("|"));

        try {
            //  JSON 형식인지 확인 (구독 응답 메시지)
            if (message.trim().startsWith("{")) {
                JsonNode jsonNode = objectMapper.readTree(message);

                //  header -> tr_id를 확인해서 국내/해외 구분
                if (jsonNode.has("header") && jsonNode.get("header").has("tr_id")) {
                    String trId = jsonNode.get("header").get("tr_id").asText();
                    String msg1 = jsonNode.has("body") && jsonNode.get("body").has("msg1")
                            ? jsonNode.get("body").get("msg1").asText()
                            : "";

                    //  구독 성공 메시지 또는 해제 메시지 처리
                    if (msg1.contains("SUBSCRIBE SUCCESS") || msg1.contains("UNSUBSCRIBE")) {
                        //  "SUBSCRIBE SUCCESS" 인데 output이 존재하는 경우만 처리
                        if (jsonNode.has("body") && jsonNode.get("body").has("output")) {
                            log.info("🔄 WebSocket 구독 관련 메시지 수신: {}", msg1);
                        } else {
                            log.info("🔄 WebSocket 구독 관련 메시지 무시: {}", msg1);
                            return;
                        }
                    }

                    //  실시간 데이터가 JSON으로 오는 경우 처리
                    if ("HDFSCNT0".equals(trId) && jsonNode.has("body") && jsonNode.get("body").has("output")) {
                        String trKey = jsonNode.get("header").has("tr_key") ?
                                jsonNode.get("header").get("tr_key").asText() : "";

                        // DNAS로 시작하는지 검증 후 처리
                        if (!trKey.startsWith("DNAS")) {
                            log.warn("⚠ 잘못된 해외 주식 tr_key 형식: {}", trKey);
                            // 오류가 있어도 계속 진행, 실제 데이터 형식 확인용
                        }

                        MarketDataDTO marketData = parseForeignMarketData(jsonNode);
                        if (marketData != null) {
                            processMarketData(marketData);
                        } else {
                            log.warn("⚠ 해외 주식 데이터 파싱 실패: {}", message);
                        }
                        return;
                    }
                }

                log.warn("⚠ JSON 메시지 수신됨. 예상치 않은 데이터 형식: {}", message);
                return;
            }

            //  파이프(|) 형식인지 확인 (실시간 데이터)
            if (message.contains("|")) {
                String[] parts = message.split("\\|");
                if (parts.length > 3) {
                    String trId = parts[1]; // TR ID 추출 (H0STCNT0 또는 HDFSCNT0)
                    log.info("📝 파이프 형식 데이터 - TR ID: {}", trId);

                    //  TR ID로 국내/해외 구분하여 처리
                    if ("H0STCNT0".equals(trId)) {
                        // 국내 주식 데이터 처리
                        MarketDataDTO marketData = parseMarketData(message);
                        if (marketData != null) {
                            processMarketData(marketData);
                        } else {
                            log.warn("⚠ 국내 주식 데이터 파싱 실패: {}", message);
                        }
                    } else if ("HDFSCNT0".equals(trId)) {
                        // 해외 주식 파이프 형식 데이터 처리
                        //log.info("📊 해외 주식 파이프 형식 데이터 수신: {}", message);

                        try {
                            // 데이터 부분 추출
                            String[] dataParts = parts[3].split("\\^");

                            // 맨 앞에 있는 종목 정보만 처리 (첫 번째 데이터만 처리)
                            if (dataParts.length >= 15) {
                                String symbol = dataParts[1];  // AAPL 형태 (순수 심볼)

                                // 주요 필드 값 추출
                                double price = parseDoubleSafe(dataParts[11]);  // LAST(현재가)
                                double high = parseDoubleSafe(dataParts[9]);    // HIGH(고가)
                                double low = parseDoubleSafe(dataParts[10]);    // LOW(저가)
                                double changeRate = parseDoubleSafe(dataParts[14]); // RATE(등락률)

                                // MarketDataDTO 생성 및 처리
                                MarketDataDTO marketData = new MarketDataDTO(symbol, price, changeRate);
                                processMarketData(marketData);

                                log.info("📈 해외 주식 데이터 처리 완료: {} - 가격: {}, 변동률: {}%",
                                        symbol, price, changeRate);
                            } else {
                                log.warn("⚠ 해외 주식 데이터 필드 수 부족: {}", Arrays.toString(dataParts));
                            }
                        } catch (Exception e) {
                            log.error("❌ 해외 주식 파이프 데이터 파싱 오류: {}", e.getMessage(), e);
                        }
                    }
                } else {
                    log.warn("⚠ 잘못된 파이프 데이터 형식: {}", message);
                }
                return;
            }

            //  이외의 형식은 일단 로깅만
            log.warn("⚠ 알 수 없는 메시지 형식: {}", message);

        } catch (JsonProcessingException jsonEx) {
            log.error("❌ JSON 파싱 오류: {}", message, jsonEx);
        } catch (Exception e) {
            log.error("❌ 데이터 처리 오류: {}", message, e);
        }
    }


    private void sendTargetPriceEvent(String userEmail, String symbol, double tradePrice, double targetPrice, String condition) {
        try {
            ObjectNode eventData = objectMapper.createObjectNode();
            eventData.put("userEmail", userEmail);
            eventData.put("symbol", symbol);
            eventData.put("currentPrice", tradePrice);
            eventData.put("targetPrice", targetPrice);
            eventData.put("condition", condition);
            eventData.put("timestamp", System.currentTimeMillis());

            //  목표 가격 도달 이벤트를 Kafka로 전송
            kafkaTemplate.send("target-price-alert", eventData.toString());
            log.info("🚀 목표 가격 도달 Kafka 이벤트 발송: [{}] {} → 목표가 {} ({})", userEmail, symbol, targetPrice, condition.toUpperCase());

        } catch (Exception e) {
            log.error("❌ 목표 가격 이벤트 전송 실패: {}", e.getMessage());
        }
    }



    private MarketDataDTO parseMarketData(String message) {
        try {
            String[] parts = message.split("\\|");
            if (parts.length <= 3) {
                log.error("❌ 국내 주식 데이터 포맷 오류: {}", message);
                return null;
            }

            String[] dataFields = parts[3].split("\\^");
            if (dataFields.length < 10) {
                log.error("❌ 국내 주식 필드 부족: {}", Arrays.toString(dataFields));
                return null;
            }

            String symbol = dataFields[0];
            double price = parseDoubleSafe(dataFields[2]);
            double changeRate = parseDoubleSafe(dataFields[5]);
            double high = parseDoubleSafe(dataFields[8]);
            double low = parseDoubleSafe(dataFields[9]);

            return new MarketDataDTO(symbol, price, changeRate );
        } catch (Exception e) {
            log.error("❌ 국내 주식 데이터 파싱 오류: {}", message, e);
            return null;
        }
    }


    private MarketDataDTO parseForeignMarketData(JsonNode jsonNode) {
        try {
            if (!jsonNode.has("body") || !jsonNode.get("body").has("output")) {
                log.error("❌ 해외 주식 데이터 포맷 오류: {}", jsonNode);
                return null;
            }

            JsonNode output = jsonNode.get("body").get("output");

            //  필드 존재 여부 체크 후 처리
            if (!output.has("SYMB") || !output.has("LAST") || !output.has("RATE") || !output.has("HIGH") || !output.has("LOW")) {
                log.error("❌ 해외 주식 데이터 필드 누락: {}", output);
                return null;
            }

            String symbol = output.get("SYMB").asText();
            double price = parseDoubleSafe(output.get("LAST").asText());
            double high = parseDoubleSafe(output.get("HIGH").asText());
            double low = parseDoubleSafe(output.get("LOW").asText());
            double changeRate = parseDoubleSafe(output.get("RATE").asText());

            return new MarketDataDTO(symbol, price, high,low, changeRate );
        } catch (Exception e) {
            log.error("❌ 해외 주식 데이터 파싱 오류: {}", jsonNode, e);
            return null;
        }
    }




    private double parseDoubleSafe(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.error("❌ 숫자 변환 오류: {}", value);
            return 0.0; // 기본값 반환
        }
    }

    private void processMarketData(MarketDataDTO marketData) {
        if (marketData == null || marketData.getSymbol() == null) {
            log.warn("⚠ 유효하지 않은 시장 데이터 수신됨: {}", marketData);
            return;
        }

        String symbolKey = "stock_prices:" + marketData.getSymbol();
        String changeKey = "stock_changes:" + marketData.getSymbol();

        //  Redis에서 기존 데이터 가져오기 (중복 저장 방지)
        String lastPriceStr = (String) redisTemplate.opsForValue().get(symbolKey);
        Double lastPrice = (lastPriceStr != null) ? parseDoubleSafe(lastPriceStr) : null;

        if (lastPrice != null && lastPrice.equals(marketData.getPrice())) {
            log.info("🔄 [{}] 가격 변동 없음. Redis 업데이트 생략", marketData.getSymbol());
            return;
        }

        //  가격 변동이 있으면 Redis 저장 (10분 TTL)
        redisTemplate.opsForValue().set(symbolKey, String.valueOf(marketData.getPrice()), 10, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(changeKey, String.valueOf(marketData.getChangeRate()), 10, TimeUnit.MINUTES);

        log.info("📡 Redis 저장 완료: {} -> 가격: {}, 변동률: {}", marketData.getSymbol(), marketData.getPrice(), marketData.getChangeRate());
        //  목표 가격 조회 및 체크
        Map<Object, Object> targetPrices = redisTemplate.opsForHash().entries("target_prices");

        for (Map.Entry<Object, Object> entry : targetPrices.entrySet()) {
            try {
                String key = entry.getKey().toString();
                Double targetPrice = parseDoubleSafe(entry.getValue().toString());

                Object conditionObj = redisTemplate.opsForHash().get("target_conditions", key);
                if (conditionObj == null) continue;

                String conditionStr = conditionObj.toString().toUpperCase();
                TargetPriceCondition condition = TargetPriceCondition.valueOf(conditionStr);

                String[] keyParts = key.split(":");
                if (keyParts.length != 2) {
                    log.warn("⚠ 잘못된 Key 형식: {}", key);
                    continue;
                }

                String userEmail = keyParts[0];
                String symbol = keyParts[1];

                if (marketData.getSymbol().equals(symbol)) {
                    String alertKey = "alert_sent:" + userEmail + ":" + symbol;

                    if (condition == TargetPriceCondition.ABOVE && marketData.getPrice() >= targetPrice) {
                        if (redisTemplate.opsForValue().get(alertKey) == null) { // 이전에 알림 보낸 적 없음
                            sendTargetPriceEvent(userEmail, symbol, marketData.getPrice(), targetPrice, condition.name());
                            redisTemplate.opsForValue().set(alertKey, "sent", 24, TimeUnit.HOURS); // 24시간 동안 저장
                        }
                    } else if (condition == TargetPriceCondition.BELOW && marketData.getPrice() <= targetPrice) {
                        if (redisTemplate.opsForValue().get(alertKey) == null) { // 이전에 알림 보낸 적 없음
                            sendTargetPriceEvent(userEmail, symbol, marketData.getPrice(), targetPrice, condition.name());
                            redisTemplate.opsForValue().set(alertKey, "sent", 24, TimeUnit.HOURS); // 24시간 동안 저장
                        }
                    }
                }

            } catch (Exception e) {
                log.error("❌ 목표 가격 비교 중 오류: key={}, value={}", entry.getKey(), entry.getValue(), e);
            }
        }

        //  변동률 ±5% 이상 시 Kafka 전송
        if (marketData.getChangeRate() != null && Math.abs(marketData.getChangeRate()) > 5) {
            kafkaProducerClient.sendMarketData(marketData);
            log.info("📡 Kafka 전송 완료: {}", marketData);
        }
    }



    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("🚪 WebSocket 연결 종료: {}", reason);
    }

    @Override
    public void onError(Exception ex) {
        log.error("⚠️ WebSocket 오류 발생", ex);
    }



}
