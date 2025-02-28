package com.example.msaasset.client;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class KisTokenCache {

    private final StringRedisTemplate redisTemplate; // Redis를 사용하는 템플릿

    private static final String TOKEN_KEY = "kis:accessToken"; // Redis에 저장될 키


    public void saveToken(String token, long expiryTime) {
        redisTemplate.opsForValue().set(TOKEN_KEY, token); // 토큰 저장
        redisTemplate.expire(TOKEN_KEY, 1, TimeUnit.DAYS);
    }


    public String getToken() {
        return redisTemplate.opsForValue().get(TOKEN_KEY);
    }


    public boolean isTokenExpired() {
        // Redis에서 TTL 확인
        Long ttl = redisTemplate.getExpire(TOKEN_KEY, TimeUnit.MILLISECONDS);

        // TTL이 -2면 키가 없으므로 만료됨, -1이면 만료 설정이 없으므로 만료된 것으로 간주
        return ttl <= 0;
    }
}