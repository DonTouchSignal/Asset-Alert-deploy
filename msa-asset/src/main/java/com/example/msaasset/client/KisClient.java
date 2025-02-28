package com.example.msaasset.client;

import com.example.msaasset.dto.MarketDataDTO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@Data
@RequiredArgsConstructor
public class KisClient {

    private final KisTokenCache kisTokenCache;

    private String accessToken; // 액세스 토큰 캐싱
    private long tokenExpiryTime; // 만료 시간(Unix Time 기준)

    @Value("${kis.appkey}")
    private String appKey;

    @Value("${kis.appsecret}")
    private String appSecret;

    @Value("${kis.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    private String trId = "FHKST01010100";




    private HttpHeaders createHeaders(String trId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + getAccessToken());
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", trId);
        log.info("Request Headers: {}", headers); // 요청 전에 헤더 로깅
        return headers;
    }

    private String getAccessToken() {
        // 1. Redis에서 토큰이 유효하면 그대로 반환
        if (!kisTokenCache.isTokenExpired()) {
            log.info("✅ 기존 Redis의 토큰을 재사용합니다.");
            return kisTokenCache.getToken();
        }

        // 2. 토큰이 만료되었거나 없으면 새 토큰 발급
        log.info("⏰ 토큰이 만료되었거나 없습니다. 새 토큰을 발급합니다.");

        String url = baseUrl + "/oauth2/tokenP";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        String requestBody = "{ \"grant_type\": \"client_credentials\", \"appkey\": \"" + appKey + "\", \"appsecret\": \"" + appSecret + "\" }";

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);

        // 3. 응답 결과 확인 (토큰 발급 실패 시 예외 처리 추가)
        if (response.getBody() == null || !response.getBody().has("access_token")) {
            throw new RuntimeException("❌ KIS API 인증 토큰을 가져올 수 없습니다.");
        }

        String newAccessToken = response.getBody().get("access_token").asText();
        int expiresInSeconds = response.getBody().has("expires_in") ?
                response.getBody().get("expires_in").asInt() : 80400;  // 만료시간 기본값 1일(86400초)

        // 4. 만료 시간 계산 및 Redis에 저장
        long expiryTime = System.currentTimeMillis() + (expiresInSeconds * 1000L);
        kisTokenCache.saveToken(newAccessToken, expiryTime);

        log.info("🆕 새 토큰이 발급되어 Redis에 저장되었습니다.");
        return newAccessToken;
    }



    /**
     * 국내 주식 시세 조회 ver1
     */
    public MarketDataDTO getDomesticStockPrice(String stockCode) {
        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + stockCode;

        HttpEntity<String> request = new HttpEntity<>(createHeaders(trId));
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, request, JsonNode.class);

        if (response.getBody() == null || !response.getBody().has("output")) {
            log.error("❌ 국내 주식 시세 데이터를 가져올 수 없습니다. 응답: {}", response);
            throw new RuntimeException("국내 주식 시세 데이터를 가져올 수 없습니다.");
        }

        JsonNode data = response.getBody().get("output");

        if (!data.has("stck_prpr")) {
            log.error("❌ 국내 주식 시세 응답에 예상된 필드가 없습니다. 응답: {}", data);
            throw new RuntimeException("국내 주식 시세 응답에 예상된 필드가 없습니다.");
        }

        return new MarketDataDTO(
                stockCode,
                data.get("stck_prpr").asDouble(),  //현재 시세
                data.get("stck_hgpr").asDouble(),
                data.get("stck_lwpr").asDouble(),
                data.get("acml_vol").asDouble(),
                data.get("prdy_ctrt").asDouble(),
                data.get("acml_vol").asDouble()  //누적거래량으로 가져옴

        );
    }


    /**
     * 해외 주식 시세 조회
     */
    public MarketDataDTO getForeignStockPrice(String stockCode) {
        String url = baseUrl + "/uapi/overseas-price/v1/quotations/price?EXCD=NAS&SYMB=" + stockCode;

        HttpEntity<String> request = new HttpEntity<>(createHeaders("HHDFS00000300"));
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, request, JsonNode.class);

        if (response.getBody() == null || !response.getBody().has("output")) {
            log.error("❌ 해외 주식 시세 데이터를 가져올 수 없습니다. 응답: {}", response);
            throw new RuntimeException("해외 주식 시세 데이터를 가져올 수 없습니다.");
        }

        JsonNode data = response.getBody().get("output");

        if (!data.has("last")) {
            log.error("❌ 해외 주식 시세 응답에 예상된 필드가 없습니다. 응답: {}", data);
            throw new RuntimeException("해외 주식 시세 응답에 예상된 필드가 없습니다.");
        }

        return new MarketDataDTO(
                stockCode,
                data.get("last").asDouble(),
                data.get("tvol").asDouble(),
                data.get("rate").asDouble(),
                data.get("tamt").asDouble()
        );
    }
    public boolean isDomesticMarketOpen() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(15, 30));
    }

    public boolean isUSMarketOpen() {
        // 한국시간 기준 미국 시장 시간 (EST+14시간)
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(23, 30)) || now.isBefore(LocalTime.of(6, 0));
    }



}
