package com.lumina.common;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimpleRateLimiter {
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public void check(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        Deque<Long> queue = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            long cutoff = now - window.toMillis();
            while (!queue.isEmpty() && queue.peekFirst() < cutoff) {
                queue.removeFirst();
            }
            if (queue.size() >= limit) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests. Wait a moment and try again.");
            }
            queue.addLast(now);
        }
    }
}
