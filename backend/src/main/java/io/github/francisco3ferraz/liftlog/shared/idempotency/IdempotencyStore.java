package io.github.francisco3ferraz.liftlog.shared.idempotency;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Remembers each user's {@code Idempotency-Key}s so a retried request gets the first response instead of running
 * again. Every call commits on its own; the primary key on {@code (user_id, key)} is what admits exactly one of
 * several concurrent first attempts.
 */
@Component
class IdempotencyStore {

    private final JdbcClient jdbc;
    private final Clock clock;

    IdempotencyStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Claims {@code key} for a request whose method, path and body hash to {@code requestHash}. Returns {@link New}
     * if the caller should handle the request and then {@link #complete} it, {@link Replay} if the same request
     * already finished, {@link InFlight} if it is still running, and {@link Conflict} if the key was used for a
     * different request.
     */
    Outcome begin(UUID userId, UUID key, String requestHash) {
        while (true) {
            int inserted = jdbc.sql("""
                            insert into idempotency_keys (user_id, key, request_hash, created_at)
                            values (?, ?, ?, ?)
                            on conflict (user_id, key) do nothing""")
                    .params(userId, key, requestHash, clock.instant().atOffset(ZoneOffset.UTC))
                    .update();
            if (inserted == 1) {
                return new New();
            }
            var existing = jdbc.sql("""
                            select request_hash, status, response_body::text as response_body
                            from idempotency_keys where user_id = ? and key = ?""")
                    .params(userId, key)
                    .query((rs, n) -> {
                        if (!rs.getString("request_hash").equals(requestHash)) {
                            return new Conflict();
                        }
                        int status = rs.getInt("status");
                        return rs.wasNull()
                                ? new InFlight()
                                : new Replay(new StoredResponse(status, rs.getString("response_body")));
                    })
                    .optional();
            // Empty only if the key was purged between the two statements; claim it again.
            if (existing.isPresent()) {
                return existing.get();
            }
        }
    }

    /** Stores the response to a request that {@link #begin} admitted as {@link New}. */
    void complete(UUID userId, UUID key, int status, @Nullable String responseBody) {
        int updated = jdbc.sql("""
                        update idempotency_keys set status = ?, response_body = ?::jsonb
                        where user_id = ? and key = ? and status is null""")
                .param(status)
                .param(responseBody)
                .param(userId)
                .param(key)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Idempotency key " + key + " is not in flight");
        }
    }

    sealed interface Outcome permits New, Replay, InFlight, Conflict {}

    record New() implements Outcome {}

    record Replay(StoredResponse response) implements Outcome {}

    record InFlight() implements Outcome {}

    record Conflict() implements Outcome {}

    /** {@code body} is JSON, or null for a response without one. */
    record StoredResponse(int status, @Nullable String body) {}
}
