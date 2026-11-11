package com.nexusops.platform.assistant;

import com.nexusops.platform.common.exception.RateLimitException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AssistantRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final AiAssistantProperties properties;
    private final ConcurrentHashMap<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public AssistantRateLimiter(AiAssistantProperties properties) {
        this.properties = properties;
    }

    public void checkAndConsume(UUID organizationId, UUID userId) {
        int limit = Math.max(1, properties.getRateLimitPerMinute());
        String key = organizationId + ":" + userId;
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);

        Deque<Instant> timestamps = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= limit) {
                throw new RateLimitException(
                        "AI assistant rate limit exceeded (" + limit + " requests/minute). Please retry shortly.");
            }
            timestamps.addLast(now);
        }
    }
}
