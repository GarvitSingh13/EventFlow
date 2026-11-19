package com.eventflow.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final Map<UUID, TokenBucket> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(UUID endpointId, int rateLimitPerSecond) {
        if (rateLimitPerSecond <= 0) {
            return true; // No rate limit
        }

        TokenBucket bucket = buckets.computeIfAbsent(endpointId, id -> new TokenBucket(rateLimitPerSecond));
        return bucket.tryConsume();
    }

    private static class TokenBucket {
        private final double capacity;
        private final double refillRatePerNano;
        private double tokens;
        private long lastRefillNanos;

        public TokenBucket(double rateLimitPerSecond) {
            this.capacity = rateLimitPerSecond;
            this.refillRatePerNano = rateLimitPerSecond / 1_000_000_000.0;
            this.tokens = rateLimitPerSecond;
            this.lastRefillNanos = System.nanoTime();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsedNanos = now - lastRefillNanos;
            if (elapsedNanos > 0) {
                double tokensToAdd = elapsedNanos * refillRatePerNano;
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillNanos = now;
            }
        }
    }
}
