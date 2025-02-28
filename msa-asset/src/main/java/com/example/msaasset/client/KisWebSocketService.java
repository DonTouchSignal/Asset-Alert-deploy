package com.example.msaasset.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.ApplicationContext;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisWebSocketService {
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate; // Redis 사용
    private final ApplicationContext applicationContext;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    @Value("${kis.appkey}")
    private String appKey;

    @Value("${kis.appsecret}")
    private String appSecret;

    @Value("${kis.base-url}")
    private String baseUrl;

    private static final String APPROVAL_KEY_REDIS_KEY = "kis:approvalKey"; // Redis Key 설정

    /**
     * WebSocket Approval Key 발급 (Redis 저장 추가)
     */
    public String getApprovalKey() {
        // Redis에서 Approval Key 조회
        String cachedApprovalKey = redisTemplate.opsForValue().get(APPROVAL_KEY_REDIS_KEY);
        if (cachedApprovalKey != null) {
            log.info("✅ Redis에서 기존 Approval Key 재사용");
            return cachedApprovalKey;
        }

        log.info("🔑 새로운 WebSocket Approval Key 발급 요청");

        String url = baseUrl + "/oauth2/Approval";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        // Access Token 제거 후, API 문서에 맞게 요청 구성
        String requestBody = String.format(
                "{ \"grant_type\": \"client_credentials\", \"appkey\": \"%s\", \"secretkey\": \"%s\" }",
                appKey, appSecret // **Access Token 사용 안 함**
        );

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);

        if (response.getBody() == null || !response.getBody().has("approval_key")) {
            throw new RuntimeException("❌ WebSocket Approval Key 발급 실패");
        }

        String newApprovalKey = response.getBody().get("approval_key").asText();

        // Redis에 저장 (TTL: 24시간)
        redisTemplate.opsForValue().set(APPROVAL_KEY_REDIS_KEY, newApprovalKey, 24, TimeUnit.HOURS);

        return newApprovalKey;
    }








}
