package cc.openxiot.device.api.accesspoint.server.limiter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private static final int BURST_CAPACITY = 20;

    @Test
    void burstCapacity_allowsUpTo20Acquires() {
        RateLimiter limiter = new RateLimiter();
        String key = "device-001";

        for (int i = 0; i < BURST_CAPACITY; i++) {
            assertTrue(limiter.tryAcquire(key), "acquire #" + (i + 1) + " should succeed");
        }
    }

    @Test
    void rateLimited_rejectsExceedingBurst() {
        RateLimiter limiter = new RateLimiter();
        String key = "device-002";

        for (int i = 0; i < BURST_CAPACITY; i++) {
            limiter.tryAcquire(key);
        }

        assertFalse(limiter.tryAcquire(key), "21st acquire should be rejected");
    }

    @Test
    void refillAfterTime_restoresTokens() throws InterruptedException {
        RateLimiter limiter = new RateLimiter();
        String key = "device-003";

        // drain all tokens
        for (int i = 0; i < BURST_CAPACITY; i++) {
            limiter.tryAcquire(key);
        }
        assertFalse(limiter.tryAcquire(key), "21st should be rejected before refill");

        // wait for ~2 tokens to refill (10 tokens/sec -> 200ms = 2 tokens)
        Thread.sleep(250);

        // should get 2 refilled tokens
        assertTrue(limiter.tryAcquire(key), "first refilled token");
        assertTrue(limiter.tryAcquire(key), "second refilled token");
        // third should fail (only 2 refilled)
        assertFalse(limiter.tryAcquire(key), "third should fail after consuming refill");
    }

    @Test
    void perKeyIsolation() {
        RateLimiter limiter = new RateLimiter();

        // drain key-a fully
        for (int i = 0; i < BURST_CAPACITY; i++) {
            limiter.tryAcquire("key-a");
        }
        assertFalse(limiter.tryAcquire("key-a"));

        // key-b should still have full burst
        for (int i = 0; i < BURST_CAPACITY; i++) {
            assertTrue(limiter.tryAcquire("key-b"), "key-b acquire #" + (i + 1) + " should succeed");
        }
    }

    @Test
    void remove_clearsBucket() {
        RateLimiter limiter = new RateLimiter();
        String key = "device-004";

        limiter.tryAcquire(key);
        limiter.remove(key);

        // after remove, the bucket is recreated with full capacity
        for (int i = 0; i < BURST_CAPACITY; i++) {
            assertTrue(limiter.tryAcquire(key), "acquire #" + (i + 1) + " after remove should succeed");
        }
    }
}
