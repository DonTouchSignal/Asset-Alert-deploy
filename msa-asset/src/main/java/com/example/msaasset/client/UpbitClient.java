package com.example.msaasset.client;

import com.example.msaasset.dto.MarketDataDTO;
import java.nio.charset.StandardCharsets;
import com.example.msaasset.dto.StockDTO;
import com.example.msaasset.entity.TargetPriceCondition;
import com.example.msaasset.kafka.KafkaProducer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.websocket.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@ClientEndpoint
@Component
public class UpbitClient {
    private static final String WEBSOCKET_URL = "wss://api.upbit.com/websocket/v1";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaProducer kafkaProducerClient;
    private Session webSocketSession;

    public UpbitClient(WebClient.Builder webClientBuilder, RedisTemplate<String, Object> redisTemplate, KafkaTemplate<String, String> kafkaTemplate, KafkaProducer kafkaProducerClient) throws Exception {
        this.webClient = webClientBuilder.baseUrl("https://api.upbit.com/v1").build();
        this.objectMapper = new ObjectMapper();
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProducerClient = kafkaProducerClient;
    }

    public List<StockDTO> fetchStockList() {
        return getAllMarketSymbols().stream()
                .map(dto -> new StockDTO(
                        dto.getSymbol(),
                        dto.getKoreanName(),
                        dto.getEnglishName(),
                        dto.getMarket()
                )) // StockDTO 변환
                .collect(Collectors.toList());
    }

    /**
     *  1. 애플리케이션 시작 시 업비트 종목 리스트 가져오기
     */
    @PostConstruct
    public void initialize() {
        List<String> symbols = getAllMarketSymbols().stream()
                .map(StockDTO::getSymbol)
                .collect(Collectors.toList());


        if (!symbols.isEmpty()) {
            connectToWebSocket(symbols);
        } else {
            log.warn("⚠️ 업비트 종목 리스트가 비어 있습니다.");
        }

    }

    /**
     *  2. 업비트에서 종목 리스트 가져오기 (REST API)
     */
    public List<StockDTO> getAllMarketSymbols() {
        JsonNode marketData = webClient.get()
                .uri("/market/all")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (marketData == null || !marketData.isArray()) {
            return List.of();
        }

        return StreamSupport.stream(marketData.spliterator(), false)
                .map(node -> new StockDTO(
                        node.path("market").asText(),      // 마켓 코드
                        node.path("korean_name").asText(), // 한국어 이름
                        node.path("english_name").asText(), // 영어 이름
                        "Upbit"
                ))
                .collect(Collectors.toList());
    }


    /**
     *  3. WebSocket 연결 (실시간 가격 수신)
     */


    // 클래스 맨 위에 추가할 필드
    private final Set<String> processedCryptoSymbols = new HashSet<>();

    public void connectToWebSocket(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.error("❌ WebSocket 구독 요청 실패: 구독할 종목이 없음.");
            return;
        }

        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            this.webSocketSession = container.connectToServer(this, new URI(WEBSOCKET_URL));
            log.info("📡 WebSocket 연결됨. 종목 구독 요청 시작...");

            if (webSocketSession != null && webSocketSession.isOpen()) {
                log.info("✅ WebSocket 연결 상태: 열려 있음");
            } else {
                log.error("❌ WebSocket 연결 상태: 닫혀 있음");
            }

            // 추적 세트 초기화
            processedCryptoSymbols.clear();

            // 최대 10개씩 그룹화
            List<List<String>> batchedSymbols = batchList(symbols);
            log.info("📊 총 {}개 종목을 {}개 배치로 나눠서 구독합니다.", symbols.size(), batchedSymbols.size());

            // 각 배치를 2초 간격으로 구독
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            for (int i = 0; i < batchedSymbols.size(); i++) {
                final int batchIndex = i;
                scheduler.schedule(() -> {
                    List<String> batch = batchedSymbols.get(batchIndex);
                    try {
                        String subscribeMessage = objectMapper.writeValueAsString(List.of(
                                new WebSocketRequest("batch-" + batchIndex),
                                new WebSocketRequest("ticker", batch)
                        ));

                        log.info("📡 WebSocket 구독 요청 (배치 {}/{}): {} 종목",
                                batchIndex + 1, batchedSymbols.size(), batch.size());
                        sendMessage(subscribeMessage);

                        // 처리된 심볼 추적 추가
                        processedCryptoSymbols.addAll(batch);
                        log.info("✅ 현재까지 처리된 암호화폐 종목 수: {}/{}",
                                processedCryptoSymbols.size(), symbols.size());

                        // 모든 종목 처리 완료 체크
                        if (processedCryptoSymbols.size() >= symbols.size()) {
                            log.info("🔄🔄🔄 암호화폐 전체 순환 완료! 처리 종목 수: {}/{}",
                                    processedCryptoSymbols.size(), symbols.size());
                        }

                    } catch (JsonProcessingException e) {
                        log.error("❌ JSON 직렬화 오류: {}", e.getMessage());
                    }
                }, i * 500L, TimeUnit.MILLISECONDS); // 각 배치 사이에 0.5초 간격
            }

            // 핑 메시지 시작
            startPingTask();

        } catch (Exception e) {
            log.error("❌ WebSocket 연결 실패", e);
        }
    }



    private List<List<String>> batchList(List<String> originalList) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < originalList.size(); i += 6) {
            batches.add(originalList.subList(i, Math.min(originalList.size(), i + 8)));
        }
        return batches;
    }

    /**
     *  4. WebSocket 메시지 수신
     */


    @OnMessage
    public void onMessage(ByteBuffer message) {
        try {
            // ByteBuffer를 UTF-8 문자열로 변환
            String decodedMessage = StandardCharsets.UTF_8.decode(message).toString();
            log.info("📩업비트 WebSocket 데이터 수신: {}", decodedMessage);

            // JSON 데이터 파싱
            JsonNode data = objectMapper.readTree(decodedMessage);
            log.info("✅ JSON 데이터 파싱 성공: {}", data);

            // handleMessage 호출하여 처리
            handleMessage(decodedMessage);
        } catch (Exception e) {
            log.error("❌ 데이터 처리 오류: {}", e.getMessage(), e);
        }
    }

    /**
     *  5. WebSocket 데이터 처리 후 Redis & Kafka 전송
     */
    private void handleMessage(String message) {
        try {
            JsonNode data = objectMapper.readTree(message);

            // 'code' 필드를 문자열로 처리
            String symbol = data.has("code") ? data.get("code").asText() : "UNKNOWN";

            // 'trade_price' 필드를 숫자 타입으로 처리
            double tradePrice = data.has("trade_price")
                    ? Double.parseDouble(data.get("trade_price").asText())
                    : 0.0;

            // 'signed_change_rate' 필드를 숫자 타입으로 처리
            double changeRate = data.has("signed_change_rate")
                    ? Double.parseDouble(data.get("signed_change_rate").asText())
                    : 0.0;

            // WebSocket 데이터가 비정상적으로 수신된 경우, REST API에서 데이터 가져오기
            if (tradePrice == 0.0 || changeRate == 0.0) {
                log.warn("⚠️ WebSocket 데이터 이상 감지. REST API로 대체 데이터 요청: {}", symbol);
                fetchStockDataFromRestApi(symbol);
                return;
            }

            log.info("✅ 종목: {}, 가격: {}, 변동률: {}", symbol, tradePrice, changeRate);

            // MarketDataDTO 객체 생성
            MarketDataDTO marketData = new MarketDataDTO(symbol, tradePrice, changeRate);

            // Redis 저장 - 기존 TTL 갱신 (30분)
            redisTemplate.opsForValue().set("stock_prices:" + symbol, String.valueOf(tradePrice), 30, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set("stock_changes:" + symbol, String.valueOf(changeRate), 30, TimeUnit.MINUTES);

            log.info("📡 업비트 Redis 저장 완료: {} -> 가격: {}, 변동률: {}", symbol, tradePrice, changeRate);

            // 목표 가격 조회하고 체크
            Map<Object, Object> targetPrices = redisTemplate.opsForHash().entries("target_prices");

            for (Map.Entry<Object, Object> entry : targetPrices.entrySet()) {
                String key = (String) entry.getKey();
                Double targetPrice = Double.parseDouble((String) entry.getValue());

                String conditionStr = (String) redisTemplate.opsForHash().get("target_conditions", key);

                if (conditionStr != null) {
                    TargetPriceCondition condition = TargetPriceCondition.valueOf(conditionStr.toUpperCase());

                    String[] keyParts = key.split(":");
                    if (keyParts.length != 2) continue;

                    String userEmail = keyParts[0];
                    String targetSymbol = keyParts[1];

                    if (symbol.equals(targetSymbol)) {
                        String alertKey = "alert_sent:" + userEmail + ":" + symbol + ":" + condition;

                        // 중복 알림 방지 - 24시간 동안 유지 (하루 동안 같은 조건이면 알림 X)
                        if (redisTemplate.hasKey(alertKey)) {
                            log.warn("⚠️ 중복 알림 방지: {} - 최근 24시간 내 알림 전송됨", alertKey);
                            continue;
                        }

                        // 목표 가격 달성 시 알림 전송
                        if ((condition == TargetPriceCondition.ABOVE && tradePrice >= targetPrice) ||
                                (condition == TargetPriceCondition.BELOW && tradePrice <= targetPrice)) {

                            sendTargetPriceEvent(userEmail, symbol, tradePrice, targetPrice, condition.name());

                            // Redis에 알림 전송 기록 저장 (TTL 24시간)
                            redisTemplate.opsForValue().set(alertKey, "sent", 24, TimeUnit.HOURS);
                        }
                    }
                }
            }

            // 변동률 ±5% 이상일 때 Kafka 알림 전송
            if (Math.abs(changeRate) >= 5.0) {
                kafkaProducerClient.sendMarketData(marketData);
                log.info("🚀 Kafka 알림 발송: {}", marketData);
            }

        } catch (Exception e) {
            log.error("❌ WebSocket 메시지 처리 실패: {}", e.getMessage(), e);
        }
    }



    private void fetchStockDataFromRestApi(String symbol) {
        try {
            JsonNode response = webClient.get()
                    .uri("/ticker?markets=" + symbol)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.isArray() && !response.isEmpty()) {
                JsonNode ticker = response.get(0);
                double tradePrice = ticker.has("trade_price") ? ticker.get("trade_price").asDouble() : 0.0;
                double changeRate = ticker.has("signed_change_rate") ? ticker.get("signed_change_rate").asDouble() : 0.0;

                // Redis에 업데이트
                redisTemplate.opsForValue().set("stock_prices:" + symbol, String.valueOf(tradePrice), 30, TimeUnit.MINUTES);
                redisTemplate.opsForValue().set("stock_changes:" + symbol, String.valueOf(changeRate), 30, TimeUnit.MINUTES);

                log.info("📡 REST API 데이터 갱신 완료: {} 가격: {}, 변동률: {}", symbol, tradePrice, changeRate);
            }
        } catch (Exception e) {
            log.error("❌ REST API 데이터 가져오기 실패: {}", e.getMessage(), e);
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



    /**
     *  6. WebSocket 메시지 전송 (구독 요청)
     */
    private void sendMessage(String message) {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            try {
                webSocketSession.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("❌ WebSocket 메시지 전송 실패: {}", e.getMessage());
                reconnect();  // 메시지 전송 실패 시 재연결 시도
            }
        } else {
            log.warn("⚠️ WebSocket 세션이 닫혀 있음. {}초 후 재연결 시도.", 5);
            reconnect();
        }
    }


    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    // 자동종료 방지 핑보내기
    private void startPingTask() {
        scheduler.scheduleAtFixedRate(() -> {
            if (webSocketSession != null && webSocketSession.isOpen()) {
                try {
                    webSocketSession.getBasicRemote().sendPing(ByteBuffer.wrap(new byte[0]));
                    log.info("📡 WebSocket Ping 메시지 전송");
                } catch (IOException e) {
                    log.error("❌ Ping 전송 실패, WebSocket 재연결 시도: {}", e.getMessage());
                    reconnect();
                }
            }
        }, 25, 10, TimeUnit.SECONDS); // 10초마다 Ping 전송
    }



    /**
     *  7. WebSocket 연결 종료 시 재연결
     */
    @OnClose
    public void onClose(Session session, CloseReason reason) {
        log.warn("🚪 WebSocket 연결 종료: {}", reason != null ? reason.getReasonPhrase() : "알 수 없는 이유");
        scheduler.schedule(this::reconnect, 5, TimeUnit.SECONDS);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("❌ WebSocket 오류 발생: {}", throwable.getMessage(), throwable);
        scheduler.schedule(this::reconnect, 5, TimeUnit.SECONDS);
    }





    /**
     *  8. WebSocket 재연결 처리
     */
    //private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private int reconnectAttempts = 0;


    private void reconnect() {
        try {
            reconnectAttempts++;
            Thread.sleep(5000);
            log.info("🔄 WebSocket 재연결 시도... ({}회차)", reconnectAttempts);

            connectToWebSocket(getAllMarketSymbols().stream()
                    .map(StockDTO::getSymbol)
                    .collect(Collectors.toList()));

            reconnectAttempts = 0; // 성공하면 카운트 리셋
        } catch (InterruptedException e) {
            log.error("❌ WebSocket 재연결 실패: {}", e.getMessage());
        }
    }



    /**
     *  9. WebSocket 요청 메시지 객체
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL) // null 필드는 JSON에서 제외
    private static class WebSocketRequest {

        @JsonProperty("ticket")
        private String ticket;

        @JsonProperty("type")
        private String type;

        @JsonProperty("codes")
        private List<String> codes;

        //  티켓 요청 생성자
        public WebSocketRequest(String ticket) {
            this.ticket = ticket;
        }

        //  종목 구독 요청 생성자
        public WebSocketRequest(String type, List<String> codes) {
            this.type = type;
            this.codes = codes;
        }
    }
}
