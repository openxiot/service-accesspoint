package cc.openxiot.device.api.accesspoint.limiter;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /** 每秒设备最大消息数 */
    private static final int MAX_MESSAGES_PER_SECOND = 10;

    /** 桶容量（突发上限） */
    private static final int BURST_CAPACITY = 20;

    public boolean tryAcquire(String key) {
        return buckets.computeIfAbsent(key, k -> new TokenBucket()).tryAcquire();
    }

    public void remove(String key) {
        buckets.remove(key);
    }

    private static class TokenBucket {
        private long tokens;
        private long lastRefill;

        TokenBucket() {
            this.tokens = BURST_CAPACITY;
            this.lastRefill = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            refill();

            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsedNs = now - lastRefill;

            // 每秒补充 MAX_MESSAGES_PER_SECOND 个令牌
            long newTokens = (elapsedNs * MAX_MESSAGES_PER_SECOND) / 1_000_000_000L;
            if (newTokens > 0) {
                tokens = Math.min(tokens + newTokens, BURST_CAPACITY);
                lastRefill = now;
            }
        }
    }
}
