package io.github.francisco3ferraz.liftlog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.francisco3ferraz.liftlog.TestcontainersConfiguration;
import io.github.francisco3ferraz.liftlog.shared.Ids;
import io.github.francisco3ferraz.liftlog.shared.SoftDeletableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Import({TestcontainersConfiguration.class, AuditingListenerIT.ClockConfig.class})
class AuditingListenerIT {

    static final Instant T0 = Instant.parse("2026-01-05T10:00:00Z");
    static final Instant T1 = Instant.parse("2026-01-05T11:00:00Z");
    static final AuditActor ACTOR = AuditActor.system("auditing-test");

    @Autowired
    SettableClock clock;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        clock.set(T0);
    }

    @Test
    void insertIsRecordedAsCreateWithTheNewState() {
        var id = Ids.newV7();

        inContext("req-1", () -> em.persist(new AuditedProbe(id, "bench", "hash-1")));

        var row = onlyRowFor(id);
        assertThat(row.action()).isEqualTo("CREATE");
        assertThat(row.entityType()).isEqualTo("audited_probe");
        assertThat(row.actor()).isEqualTo("system:auditing-test");
        assertThat(row.requestId()).isEqualTo("req-1");
        assertThat(row.before()).isNull();
        assertThat(row.after())
                .containsEntry("name", "bench")
                .containsEntry("version", 0)
                .containsEntry("createdAt", "2026-01-05T10:00:00Z")
                .containsEntry("updatedAt", "2026-01-05T10:00:00Z")
                .containsEntry("deletedAt", null);
    }

    @Test
    void updateIsRecordedWithTheOldAndNewState() {
        var id = Ids.newV7();
        inContext("req-1", () -> em.persist(new AuditedProbe(id, "bench", "hash-1")));
        clock.set(T1);

        inContext("req-2", () -> {
            var probe = em.find(AuditedProbe.class, id);
            probe.rename("squat");
            assertThat(probe.name()).isEqualTo("squat");
        });

        var rows = rowsFor(id);
        assertThat(rows).extracting(AuditRow::action).containsExactly("CREATE", "UPDATE");
        var update = rows.get(1);
        assertThat(update.requestId()).isEqualTo("req-2");
        assertThat(update.before())
                .containsEntry("name", "bench")
                .containsEntry("version", 0)
                .containsEntry("updatedAt", "2026-01-05T10:00:00Z");
        assertThat(update.after())
                .containsEntry("name", "squat")
                .containsEntry("version", 1)
                .containsEntry("updatedAt", "2026-01-05T11:00:00Z");
    }

    @Test
    void softDeleteIsRecordedAsDeleteAndRestoreAsRestore() {
        var id = Ids.newV7();
        inContext("req-1", () -> em.persist(new AuditedProbe(id, "bench", "hash-1")));
        clock.set(T1);

        inContext("req-2", () -> em.find(AuditedProbe.class, id).softDelete(clock));
        inContext("req-3", () -> em.find(AuditedProbe.class, id).restore());

        var rows = rowsFor(id);
        assertThat(rows).extracting(AuditRow::action).containsExactly("CREATE", "DELETE", "RESTORE");
        assertThat(rows.get(1).before()).containsEntry("deletedAt", null);
        assertThat(rows.get(1).after()).containsEntry("deletedAt", "2026-01-05T11:00:00Z");
        assertThat(rows.get(2).before()).containsEntry("deletedAt", "2026-01-05T11:00:00Z");
        assertThat(rows.get(2).after()).containsEntry("deletedAt", null);
    }

    @Test
    void hardDeleteIsRecordedAsDeleteWithNoStateLeft() {
        var id = Ids.newV7();
        inContext("req-1", () -> em.persist(new AuditedProbe(id, "bench", "hash-1")));

        inContext("req-2", () -> em.remove(em.find(AuditedProbe.class, id)));

        var delete = rowsFor(id).get(1);
        assertThat(delete.action()).isEqualTo("DELETE");
        assertThat(delete.before()).containsEntry("name", "bench");
        assertThat(delete.after()).isNull();
    }

    @Test
    void fieldsMarkedNotAuditedAreNeverRecorded() {
        var id = Ids.newV7();
        inContext("req-1", () -> em.persist(new AuditedProbe(id, "bench", "hash-1")));

        inContext("req-2", () -> {
            var probe = em.find(AuditedProbe.class, id);
            probe.changeSecret("hash-2");
            assertThat(probe.secretHash()).isEqualTo("hash-2");
        });
        inContext("req-3", () -> em.remove(em.find(AuditedProbe.class, id)));

        var rows = rowsFor(id);
        assertThat(rows).extracting(AuditRow::action).containsExactly("CREATE", "UPDATE", "DELETE");
        assertThat(rows).allSatisfy(row -> {
            assertThat(String.valueOf(row.rawBefore())).doesNotContain("secret", "hash-");
            assertThat(String.valueOf(row.rawAfter())).doesNotContain("secret", "hash-");
        });
    }

    @Test
    void entitiesWithoutAuditedAreNotRecordedAndNeedNoContext() {
        var id = Ids.newV7();

        tx.executeWithoutResult(s -> em.persist(new UnauditedProbe(id)));

        assertThat(rowsFor(id)).isEmpty();
        String name = tx.execute(s -> em.find(UnauditedProbe.class, id).name());
        assertThat(name).isEqualTo("plain");
    }

    @Test
    void anAuditedWriteWithoutContextFailsAndRollsBack() {
        var id = Ids.newV7();

        // Thrown while flushing on commit, so it arrives translated by Spring.
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> em.persist(new AuditedProbe(id, "bench", "hash-1"))))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageStartingWith("No AuditContext bound for CREATE of audited_probe");

        assertThat(jdbc.queryForObject("select count(*) from audited_probe where id = ?", Long.class, id))
                .isZero();
        assertThat(rowsFor(id)).isEmpty();
    }

    @Test
    void rollingBackTheChangeAlsoRollsBackItsAuditRow() {
        var id = Ids.newV7();

        AuditContext.runAs(
                ACTOR,
                "req-1",
                () -> tx.executeWithoutResult(s -> {
                    em.persist(new AuditedProbe(id, "bench", "hash-1"));
                    em.flush();
                    s.setRollbackOnly();
                }));

        assertThat(rowsFor(id)).isEmpty();
    }

    private void inContext(String requestId, Runnable op) {
        AuditContext.runAs(ACTOR, requestId, () -> tx.executeWithoutResult(s -> op.run()));
    }

    private List<AuditRow> rowsFor(UUID id) {
        return jdbc.query(
                "select entity_type, action, actor, request_id, before::text, after::text from audit_log"
                        + " where entity_id = ? order by id",
                (rs, n) -> new AuditRow(
                        rs.getString("entity_type"),
                        rs.getString("action"),
                        rs.getString("actor"),
                        rs.getString("request_id"),
                        rs.getString("before"),
                        rs.getString("after"),
                        parse(rs.getString("before")),
                        parse(rs.getString("after"))),
                id);
    }

    private AuditRow onlyRowFor(UUID id) {
        var rows = rowsFor(id);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private @Nullable Map<String, @Nullable Object> parse(@Nullable String state) {
        if (state == null) {
            return null;
        }
        Map<String, @Nullable Object> map = json.readValue(state, new TypeReference<>() {});
        return map;
    }

    record AuditRow(
            String entityType,
            String action,
            String actor,
            String requestId,
            @Nullable String rawBefore,
            @Nullable String rawAfter,
            @Nullable Map<String, @Nullable Object> before,
            @Nullable Map<String, @Nullable Object> after) {}

    @Entity
    @Table(name = "audited_probe")
    @Audited
    static class AuditedProbe extends SoftDeletableEntity {

        private String name;

        @NotAudited
        private String secretHash;

        protected AuditedProbe() {
            this.name = "";
            this.secretHash = "";
        }

        AuditedProbe(UUID id, String name, String secretHash) {
            super(id);
            this.name = name;
            this.secretHash = secretHash;
        }

        String name() {
            return name;
        }

        String secretHash() {
            return secretHash;
        }

        void rename(String name) {
            this.name = name;
        }

        void changeSecret(String secretHash) {
            this.secretHash = secretHash;
        }
    }

    @Entity
    @Table(name = "base_entity_probe")
    static class UnauditedProbe extends SoftDeletableEntity {

        private String name = "plain";

        protected UnauditedProbe() {}

        UnauditedProbe(UUID id) {
            super(id);
        }

        String name() {
            return name;
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
