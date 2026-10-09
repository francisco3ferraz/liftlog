package io.github.francisco3ferraz.liftlog.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.francisco3ferraz.liftlog.TestcontainersConfiguration;
import io.github.francisco3ferraz.liftlog.shared.Ids;
import io.github.francisco3ferraz.liftlog.shared.idempotency.IdempotencyStore.Conflict;
import io.github.francisco3ferraz.liftlog.shared.idempotency.IdempotencyStore.InFlight;
import io.github.francisco3ferraz.liftlog.shared.idempotency.IdempotencyStore.New;
import io.github.francisco3ferraz.liftlog.shared.idempotency.IdempotencyStore.Outcome;
import io.github.francisco3ferraz.liftlog.shared.idempotency.IdempotencyStore.Replay;
import io.github.francisco3ferraz.liftlog.shared.idempotency.IdempotencyStore.StoredResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, IdempotencyStoreIT.ClockConfig.class})
class IdempotencyStoreIT {

    static final Instant NOW = Instant.parse("2026-01-05T10:00:00Z");
    static final String HASH = "a1".repeat(32);
    static final String OTHER_HASH = "b2".repeat(32);

    @Autowired
    IdempotencyStore store;

    @Autowired
    JdbcTemplate jdbc;

    final UUID user = Ids.newV7();
    final UUID key = Ids.newV7();

    @Test
    void firstBeginIsNewAndRecordsAnInFlightKey() {
        assertThat(store.begin(user, key, HASH)).isInstanceOf(New.class);

        var row = jdbc.queryForMap(
                "select request_hash, status, response_body, created_at from idempotency_keys"
                        + " where user_id = ? and key = ?",
                user,
                key);
        assertThat(row.get("request_hash")).isEqualTo(HASH);
        assertThat(row.get("status")).isNull();
        assertThat(row.get("response_body")).isNull();
        assertThat(row.get("created_at")).isEqualTo(Timestamp.from(NOW));
    }

    @Test
    void sameRequestWhileTheFirstIsRunningIsInFlight() {
        store.begin(user, key, HASH);

        assertThat(store.begin(user, key, HASH)).isInstanceOf(InFlight.class);
    }

    @Test
    void sameRequestAfterCompletionReplaysTheStoredResponse() {
        store.begin(user, key, HASH);
        store.complete(user, key, 201, "{\"id\": \"x\"}");

        assertThat(store.begin(user, key, HASH)).isEqualTo(new Replay(new StoredResponse(201, "{\"id\": \"x\"}")));
    }

    @Test
    void anEmptyResponseReplaysWithoutABody() {
        store.begin(user, key, HASH);
        store.complete(user, key, 204, null);

        assertThat(store.begin(user, key, HASH)).isEqualTo(new Replay(new StoredResponse(204, null)));
    }

    @Test
    void differentRequestOnACompletedKeyIsAConflict() {
        store.begin(user, key, HASH);
        store.complete(user, key, 201, "{}");

        assertThat(store.begin(user, key, OTHER_HASH)).isInstanceOf(Conflict.class);
    }

    @Test
    void differentRequestOnAnInFlightKeyIsAConflict() {
        store.begin(user, key, HASH);

        assertThat(store.begin(user, key, OTHER_HASH)).isInstanceOf(Conflict.class);
    }

    @Test
    void keysAreScopedByUser() {
        store.begin(user, key, HASH);

        assertThat(store.begin(Ids.newV7(), key, OTHER_HASH)).isInstanceOf(New.class);
    }

    @Test
    void completingAKeyThatIsNotInFlightFails() {
        assertThatIllegalStateException().isThrownBy(() -> store.complete(user, key, 200, "{}"));

        store.begin(user, key, HASH);
        store.complete(user, key, 200, "{}");

        assertThatIllegalStateException().isThrownBy(() -> store.complete(user, key, 200, "{}"));
    }

    @Test
    void concurrentBeginsOnTheSameKeyAdmitExactlyOne() throws Exception {
        int callers = 16;
        var start = new CountDownLatch(1);
        var outcomes = new ArrayList<Outcome>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Outcome>>();
            for (int i = 0; i < callers; i++) {
                Callable<Outcome> call = () -> {
                    start.await();
                    return store.begin(user, key, HASH);
                };
                futures.add(executor.submit(call));
            }
            start.countDown();
            for (var future : futures) {
                outcomes.add(future.get());
            }
        }

        assertThat(outcomes).filteredOn(New.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(InFlight.class::isInstance).hasSize(callers - 1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
