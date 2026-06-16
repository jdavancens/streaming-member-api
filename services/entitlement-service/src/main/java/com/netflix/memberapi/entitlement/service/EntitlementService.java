package com.netflix.memberapi.entitlement.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EntitlementService {

    // TTL for a stream slot: client must heartbeat within this window
    private static final Duration STREAM_TTL = Duration.ofSeconds(90);

    private final StringRedisTemplate redis;

    // In-memory plan limits cache; updated by Kafka consumer when plan changes
    private final Map<String, Integer> memberMaxStreams = new ConcurrentHashMap<>();

    public EntitlementService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record StreamEntitlement(boolean allowed, String reason,
                                    int concurrentStreams, int maxStreams) {}

    public record StreamSession(String streamId, String expiresAt) {}

    public StreamEntitlement canStream(String memberId) {
        int maxStreams = memberMaxStreams.getOrDefault(memberId, 1);
        int current = currentStreamCount(memberId);
        if (current >= maxStreams) {
            return new StreamEntitlement(false,
                "Concurrent stream limit reached (" + maxStreams + "/" + maxStreams + ")",
                current, maxStreams);
        }
        return new StreamEntitlement(true, null, current, maxStreams);
    }

    public StreamSession acquireStream(String memberId, String deviceId) {
        int maxStreams = memberMaxStreams.getOrDefault(memberId, 1);
        String slotsKey = slotsKey(memberId);

        // Prune expired slots before checking count
        redis.opsForSet().members(slotsKey);

        if (currentStreamCount(memberId) >= maxStreams) {
            throw new IllegalStateException("Concurrent stream limit reached");
        }

        String streamId = UUID.randomUUID().toString();
        String slotValue = streamId + ":" + deviceId;
        redis.opsForSet().add(slotsKey, slotValue);
        redis.expire(slotsKey, STREAM_TTL);

        // Per-slot TTL key so individual slots expire
        redis.opsForValue().set(slotKey(memberId, streamId), deviceId, STREAM_TTL);

        Instant expiresAt = Instant.now().plus(STREAM_TTL);
        return new StreamSession(streamId, expiresAt.toString());
    }

    public boolean releaseStream(String memberId, String streamId) {
        Set<String> slots = redis.opsForSet().members(slotsKey(memberId));
        if (slots != null) {
            slots.stream()
                .filter(s -> s.startsWith(streamId + ":"))
                .forEach(s -> redis.opsForSet().remove(slotsKey(memberId), s));
        }
        redis.delete(slotKey(memberId, streamId));
        return true;
    }

    public boolean heartbeat(String memberId, String streamId) {
        String key = slotKey(memberId, streamId);
        if (Boolean.FALSE.equals(redis.hasKey(key))) return false;
        redis.expire(key, STREAM_TTL);
        redis.expire(slotsKey(memberId), STREAM_TTL);
        return true;
    }

    public void updateMaxStreams(String memberId, int maxStreams) {
        memberMaxStreams.put(memberId, maxStreams);
    }

    private int currentStreamCount(String memberId) {
        Long count = redis.opsForSet().size(slotsKey(memberId));
        return count != null ? count.intValue() : 0;
    }

    private String slotsKey(String memberId) {
        return "entitlement:" + memberId + ":streams";
    }

    private String slotKey(String memberId, String streamId) {
        return "entitlement:" + memberId + ":slot:" + streamId;
    }
}
