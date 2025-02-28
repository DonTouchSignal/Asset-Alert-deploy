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

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisAuthService {

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${kis.appkey}")
    private String appKey;

    @Value("${kis.appsecret}")
    private String appSecret;

    @Value("${kis.base-url}")
    private String baseUrl;

    private static final String ACCESS_TOKEN_KEY = "kis:accessToken";

    /**
     * Redis 기반 Access Token 관리
     */
    public String getAccessToken() {
        //  Redis에서 Access Token 확인
        String cachedAccessToken = redisTemplate.opsForValue().get(ACCESS_TOKEN_KEY);
        if (cachedAccessToken != null) {
            log.info("✅ Redis에서 기존 Access Token 재사용");
            return cachedAccessToken;
        }

        log.info("🔑 새로운 Access Token 발급 요청");

        String url = baseUrl + "/oauth2/tokenP";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        String requestBody = String.format(
                "{ \"grant_type\": \"client_credentials\", \"appkey\": \"%s\", \"appsecret\": \"%s\" }",
                appKey, appSecret
        );

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);

        if (response.getBody() == null || !response.getBody().has("access_token")) {
            throw new RuntimeException("❌ KIS API Access Token 발급 실패");
        }

        String newAccessToken = response.getBody().get("access_token").asText();
        int expiresInSeconds = response.getBody().get("expires_in").asInt();

        // 2 Redis에 저장 (API 문서에 따라 6시간 동안 유지)
        redisTemplate.opsForValue().set(ACCESS_TOKEN_KEY, newAccessToken, expiresInSeconds, TimeUnit.SECONDS);
        log.info("✅ 새로운 Access Token 발급 완료 및 Redis에 저장");

        return newAccessToken;
    }
}

