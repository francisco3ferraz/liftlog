package io.github.francisco3ferraz.liftlog.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.francisco3ferraz.liftlog.TestcontainersConfiguration;
import io.github.francisco3ferraz.liftlog.shared.Ids;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

@SpringBootTest
@Import({TestcontainersConfiguration.class, IdempotencyPurgeJobIT.ClockConfig.class})
class IdempotencyPurgeJobIT {

    static final Instant NOW = Instant.parse("2026-01-12T10:00:00Z");

    @Autowired
    IdempotencyPurgeJob job;

    @Autowired
    ScheduledTaskHolder scheduledTasks;

    @Autowired
    JdbcTemplate jdbc;

    final UUID user = Ids.newV7();

    @Test
    void deletesKeysOlderThanSevenDays() {
        var expired = insertKey(NOW.minus(Duration.ofDays(7)).minusSeconds(1));
        var longExpired = insertKey(NOW.minus(Duration.ofDays(30)));

        job.purge();

        assertThat(keyExists(expired)).isFalse();
        assertThat(keyExists(longExpired)).isFalse();
    }

    @Test
    void keepsKeysUpToSevenDaysOld() {
        var exactlySevenDays = insertKey(NOW.minus(Duration.ofDays(7)));
        var recent = insertKey(NOW.minus(Duration.ofHours(1)));

        job.purge();

        assertThat(keyExists(exactlySevenDays)).isTrue();
        assertThat(keyExists(recent)).isTrue();
    }

    @Test
    void purgeRunsOnASchedule() {
        assertThat(scheduledTasks.getScheduledTasks())
                .map(ScheduledTask::toString)
                .contains(IdempotencyPurgeJob.class.getName() + ".purge");
    }

    private UUID insertKey(Instant createdAt) {
        var key = Ids.newV7();
        jdbc.update(
                "insert into idempotency_keys (user_id, key, request_hash, status, response_body, created_at)"
                        + " values (?, ?, 'h', 201, '{}'::jsonb, ?)",
                user,
                key,
                createdAt.atOffset(ZoneOffset.UTC));
        return key;
    }

    private boolean keyExists(UUID key) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from idempotency_keys where user_id = ? and key = ?)",
                Boolean.class,
                user,
                key));
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
