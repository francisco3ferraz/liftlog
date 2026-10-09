package io.github.francisco3ferraz.liftlog.shared;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.francisco3ferraz.liftlog.TestcontainersConfiguration;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, BaseEntityIT.ClockConfig.class})
class BaseEntityIT {

    static final Instant T0 = Instant.parse("2026-01-05T10:00:00.123456Z");
    static final Instant T1 = Instant.parse("2026-01-05T11:30:00.654321Z");

    @Autowired
    SettableClock clock;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @BeforeEach
    void setUp() {
        clock.set(T0);
    }

    private SimpleJpaRepository<Probe, UUID> probes() {
        return new SimpleJpaRepository<>(Probe.class, em);
    }

    @Test
    void saveWithClientSuppliedIdInsertsTheSameInstance() {
        var probe = new Probe(Ids.newV7(), "bench");
        assertThat(probe.isNew()).isTrue();

        var saved = tx.execute(s -> probes().save(probe));

        assertThat(saved).isSameAs(probe);
        assertThat(probe.isNew()).isFalse();
        assertThat(column(probe.getId(), "name", String.class)).isEqualTo("bench");
        assertThat(column(probe.getId(), "version", Long.class)).isZero();
    }

    @Test
    void insertStampsCreatedAndUpdatedAtFromTheClock() {
        var probe = new Probe(Ids.newV7(), "bench");

        tx.executeWithoutResult(s -> probes().save(probe));

        assertThat(probe.getCreatedAt()).isEqualTo(T0);
        assertThat(probe.getUpdatedAt()).isEqualTo(T0);
        assertThat(column(probe.getId(), "created_at", OffsetDateTime.class))
                .isAtSameInstantAs(T0.atOffset(ZoneOffset.UTC));
    }

    @Test
    void updateIncrementsVersionAndBumpsUpdatedAtOnly() {
        var id = Ids.newV7();
        tx.executeWithoutResult(s -> probes().save(new Probe(id, "bench")));
        clock.set(T1);

        var updated = tx.execute(s -> {
            var probe = probes().findById(id).orElseThrow();
            assertThat(probe.isNew()).isFalse();
            probe.rename("squat");
            return probe;
        });

        assertThat(updated.name()).isEqualTo("squat");
        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(updated.getCreatedAt()).isEqualTo(T0);
        assertThat(updated.getUpdatedAt()).isEqualTo(T1);
        assertThat(column(id, "version", Long.class)).isEqualTo(1);
        assertThat(column(id, "updated_at", OffsetDateTime.class)).isAtSameInstantAs(T1.atOffset(ZoneOffset.UTC));
    }

    @Test
    void timestampsAreTruncatedToWhatPostgresStores() {
        clock.set(Instant.parse("2026-01-05T10:00:00.123456789Z"));
        var probe = new Probe(Ids.newV7(), "bench");

        tx.executeWithoutResult(s -> probes().save(probe));

        assertThat(probe.getCreatedAt()).isEqualTo(Instant.parse("2026-01-05T10:00:00.123456Z"));
    }

    @Test
    void softDeleteAndRestoreRoundTrip() {
        var id = Ids.newV7();
        tx.executeWithoutResult(s -> probes().save(new Probe(id, "bench")));
        clock.set(T1);

        tx.executeWithoutResult(s -> probes().findById(id).orElseThrow().softDelete(clock));

        assertThat(column(id, "deleted_at", OffsetDateTime.class)).isAtSameInstantAs(T1.atOffset(ZoneOffset.UTC));
        var deleted = tx.execute(s -> probes().findById(id).orElseThrow());
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isEqualTo(T1);

        tx.executeWithoutResult(s -> probes().findById(id).orElseThrow().restore());

        assertThat(column(id, "deleted_at", OffsetDateTime.class)).isNull();
        var restored = tx.execute(s -> probes().findById(id).orElseThrow());
        assertThat(restored.isDeleted()).isFalse();
        assertThat(restored.getDeletedAt()).isNull();
        assertThat(restored.getVersion()).isEqualTo(2);
    }

    private <T> @Nullable T column(UUID id, String column, Class<T> type) {
        return jdbc.queryForObject("select " + column + " from base_entity_probe where id = ?", type, id);
    }

    @Entity
    @Table(name = "base_entity_probe")
    static class Probe extends SoftDeletableEntity {

        private String name;

        protected Probe() {
            this.name = "";
        }

        Probe(UUID id, String name) {
            super(id);
            this.name = name;
        }

        String name() {
            return name;
        }

        void rename(String name) {
            this.name = name;
        }
    }

    static final class SettableClock extends Clock {

        private volatile Instant now = Instant.EPOCH;

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        @Primary
        SettableClock settableClock() {
            return new SettableClock();
        }
    }
}
