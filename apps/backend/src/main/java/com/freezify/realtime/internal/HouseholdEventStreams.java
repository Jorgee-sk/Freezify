package com.freezify.realtime.internal;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open server-sent event connections, by household.
 *
 * <p>Events carry no data: they only tell clients that something changed, and clients fetch it again through the
 * normal, authorized API. State lives in memory, which is only correct while a single instance is deployed
 * (see ARCHITECTURE.md, D12).
 */
@Component
public class HouseholdEventStreams {

    /** A phone, a tablet, a couple of browser tabs. Beyond that the oldest connection is dropped. */
    static final int MAX_STREAMS_PER_MEMBER = 5;

    private record Subscriber(UUID userId, SseEmitter emitter) {}

    private final Map<UUID, List<Subscriber>> byHousehold = new ConcurrentHashMap<>();
    /** However soon the token expires, a connection is given this long. */
    private static final Duration SHORTEST = Duration.ofSeconds(1);

    private final Duration connectionTtl;
    private final Clock clock;

    HouseholdEventStreams(@Value("${freezify.realtime.connection-ttl}") Duration connectionTtl, Clock clock) {
        this.connectionTtl = connectionTtl;
        this.clock = clock;
    }

    /**
     * The caller must have checked that the user belongs to the household.
     *
     * @param tokenExpiresAt when the token the connection was opened with expires: the connection ends then
     */
    public SseEmitter open(UUID householdId, UUID userId, @Nullable Instant tokenExpiresAt) {
        // Connections end on their own, at the latest when their token expires, so that a client keeps listening
        // only for as long as it could still ask for the data.
        SseEmitter emitter = new SseEmitter(lifetime(tokenExpiresAt).toMillis());
        Subscriber subscriber = new Subscriber(userId, emitter);
        Runnable forget = () -> remove(householdId, subscriber);
        emitter.onCompletion(forget);
        emitter.onError(error -> forget.run());
        emitter.onTimeout(emitter::complete);

        List<Subscriber> subscribers = byHousehold.computeIfAbsent(householdId, id -> new CopyOnWriteArrayList<>());
        subscribers.add(subscriber);
        dropOldestBeyondLimit(subscribers, userId);

        send(householdId, subscriber, SseEmitter.event().name("connected").data("{}"));
        return emitter;
    }

    Duration lifetime(@Nullable Instant tokenExpiresAt) {
        if (tokenExpiresAt == null) {
            return connectionTtl;
        }
        Duration untilExpiry = Duration.between(clock.instant(), tokenExpiresAt);
        if (untilExpiry.compareTo(SHORTEST) < 0) {
            return SHORTEST;
        }
        return untilExpiry.compareTo(connectionTtl) < 0 ? untilExpiry : connectionTtl;
    }

    public void publish(UUID householdId, String eventName) {
        for (Subscriber subscriber : byHousehold.getOrDefault(householdId, List.of())) {
            send(householdId, subscriber, SseEmitter.event().name(eventName).data("{}"));
        }
    }

    /** Ends the connections of the members that match, e.g. someone who no longer belongs to the household. */
    public void close(UUID householdId, Predicate<UUID> userMatches) {
        for (Subscriber subscriber : byHousehold.getOrDefault(householdId, List.of())) {
            if (userMatches.test(subscriber.userId())) {
                remove(householdId, subscriber);
                subscriber.emitter().complete();
            }
        }
    }

    int openStreams(UUID householdId) {
        return byHousehold.getOrDefault(householdId, List.of()).size();
    }

    /** Keeps proxies from closing idle connections and finds out which clients are gone. */
    @Scheduled(fixedRateString = "${freezify.realtime.heartbeat}")
    void heartbeat() {
        byHousehold.forEach((householdId, subscribers) -> {
            for (Subscriber subscriber : subscribers) {
                send(householdId, subscriber, SseEmitter.event().comment("keepalive"));
            }
        });
    }

    private void send(UUID householdId, Subscriber subscriber, SseEmitter.SseEventBuilder event) {
        try {
            subscriber.emitter().send(event);
        } catch (IOException | IllegalStateException e) {
            // The client went away, or the connection already ended.
            remove(householdId, subscriber);
        }
    }

    private void dropOldestBeyondLimit(List<Subscriber> subscribers, UUID userId) {
        List<Subscriber> ofUser =
                subscribers.stream().filter(s -> s.userId().equals(userId)).toList();
        for (Subscriber oldest : ofUser.subList(0, Math.max(0, ofUser.size() - MAX_STREAMS_PER_MEMBER))) {
            subscribers.remove(oldest);
            oldest.emitter().complete();
        }
    }

    private void remove(UUID householdId, Subscriber subscriber) {
        byHousehold.computeIfPresent(householdId, (id, subscribers) -> {
            subscribers.remove(subscriber);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }
}
