package io.github.francisco3ferraz.liftlog.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.francisco3ferraz.liftlog.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, EventPublicationIT.ListenerConfig.class})
class EventPublicationIT {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    IncompleteEventPublications incompletePublications;

    @Autowired
    FlakyListener listener;

    @Autowired
    EventPublicationPurgeJob purgeJob;

    @Autowired
    ScheduledTaskHolder scheduledTasks;

    @Autowired
    Environment environment;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Test
    void failingListenerLeavesAnIncompletePublicationThatIsRetried() {
        var event = new SomethingHappened(UUID.randomUUID());

        tx.executeWithoutResult(status -> events.publishEvent(event));

        await().until(() -> listener.attempts(event) == 1);
        await().until(() -> Boolean.FALSE.equals(isCompleted(event)));

        incompletePublications.resubmitIncompletePublications(publication -> true);

        await().until(() -> Boolean.TRUE.equals(isCompleted(event)));
        assertThat(listener.attempts(event)).isEqualTo(2);
    }

    @Test
    void outstandingPublicationsAreRepublishedOnRestart() {
        assertThat(environment.getProperty(
                        "spring.modulith.events.republish-outstanding-events-on-restart", Boolean.class))
                .isTrue();
    }

    @Test
    void purgeDeletesCompletedPublicationsOlderThanSevenDays() {
        var now = clock.instant();
        var old = insertPublication(
                now.minus(Duration.ofDays(8)), now.minus(Duration.ofDays(7)).minusSeconds(60));

        purgeJob.purge();

        assertThat(publicationExists(old)).isFalse();
    }

    @Test
    void purgeKeepsRecentlyCompletedAndIncompletePublications() {
        var now = clock.instant();
        var recent = insertPublication(now.minus(Duration.ofDays(8)), now.minus(Duration.ofDays(6)));
        var incomplete = insertPublication(now.minus(Duration.ofDays(30)), null);

        purgeJob.purge();

        assertThat(publicationExists(recent)).isTrue();
        assertThat(publicationExists(incomplete)).isTrue();
    }

    @Test
    void purgeRunsOnASchedule() {
        assertThat(scheduledTasks.getScheduledTasks())
                .map(ScheduledTask::toString)
                .contains(EventPublicationPurgeJob.class.getName() + ".purge");
    }

    private @Nullable Boolean isCompleted(SomethingHappened event) {
        return jdbc
                .query(
                        "select completion_date is not null from event_publication where serialized_event like ?",
                        (rs, row) -> rs.getBoolean(1),
                        "%" + event.id() + "%")
                .stream()
                .findFirst()
                .orElse(null);
    }

    private UUID insertPublication(Instant publishedAt, @Nullable Instant completedAt) {
        var id = UUID.randomUUID();
        jdbc.update(
                "insert into event_publication (id, listener_id, event_type, serialized_event, publication_date,"
                        + " completion_date, status, completion_attempts) values (?, 'listener', 'type', '{}', ?, ?, ?,"
                        + " 1)",
                id,
                publishedAt.atOffset(ZoneOffset.UTC),
                completedAt == null ? null : completedAt.atOffset(ZoneOffset.UTC),
                completedAt == null ? "FAILED" : "COMPLETED");
        return id;
    }

    private boolean publicationExists(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("select exists (select 1 from event_publication where id = ?)", Boolean.class, id));
    }

    record SomethingHappened(UUID id) {}

    /** Fails the first delivery of each event and accepts the next one. */
    static class FlakyListener {

        private final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();

        @ApplicationModuleListener
        void on(SomethingHappened event) {
            if (attempts.computeIfAbsent(event.id(), id -> new AtomicInteger()).incrementAndGet() == 1) {
                throw new IllegalStateException("first delivery fails");
            }
        }

        int attempts(SomethingHappened event) {
            var count = attempts.get(event.id());
            return count == null ? 0 : count.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {

        @Bean
        FlakyListener flakyListener() {
            return new FlakyListener();
        }
    }
}
