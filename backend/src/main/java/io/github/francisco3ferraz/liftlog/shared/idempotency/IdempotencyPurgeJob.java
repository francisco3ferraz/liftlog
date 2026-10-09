package io.github.francisco3ferraz.liftlog.shared.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes idempotency keys once they are too old for a client to still be retrying the request. */
@Component
class IdempotencyPurgeJob {

    static final Duration TTL = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(IdempotencyPurgeJob.class);

    private final JdbcClient jdbc;
    private final Clock clock;

    IdempotencyPurgeJob(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    void purge() {
        int deleted = jdbc.sql("delete from idempotency_keys where created_at < ?")
                .param(clock.instant().minus(TTL).atOffset(ZoneOffset.UTC))
                .update();
        if (deleted > 0) {
            log.info("Purged {} expired idempotency keys", deleted);
        }
    }
}
