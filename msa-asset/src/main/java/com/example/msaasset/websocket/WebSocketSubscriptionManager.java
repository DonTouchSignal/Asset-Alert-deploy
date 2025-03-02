package com.example.msaasset.websocket;

import com.example.msaasset.client.UpbitClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSubscriptionManager {
    private final UpbitClient upbitClient;
    private final Map<String, Integer> subscriptionCounts = new ConcurrentHashMap<>();


    public WebSocketSubscriptionManager(UpbitClient upbitClient) {
        this.upbitClient = upbitClient;
    }

    // 종목 구독 요청
    public void subscribeToSymbol(String symbol) {
        subscriptionCounts.compute(symbol, (key, count) -> count == null ? 1 : count + 1);
        if (subscriptionCounts.get(symbol) == 1) {
            // 처음 구독하는 경우에만 웹소켓 구독 요청
            upbitClient.subscribeToSingleSymbol(symbol);
        }
    }

    // 종목 구독 해제
    public void unsubscribeFromSymbol(String symbol) {
        subscriptionCounts.compute(symbol, (key, count) -> count == null || count <= 1 ? null : count - 1);
        if (!subscriptionCounts.containsKey(symbol)) {
            // 더 이상 구독자가 없는 경우 웹소켓 구독 해제
            upbitClient.unsubscribeFromSymbol(symbol);
        }
    }
}