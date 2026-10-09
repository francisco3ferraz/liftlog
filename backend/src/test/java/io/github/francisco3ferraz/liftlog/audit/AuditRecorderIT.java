package io.github.francisco3ferraz.liftlog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;

import io.github.francisco3ferraz.liftlog.TestcontainersConfiguration;
import io.github.francisco3ferraz.liftlog.shared.Ids;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, AuditRecorderIT.ClockConfig.class})
class AuditRecorderIT {

    static final Instant NOW = Instant.parse("2026-01-05T10:00:00Z");

    @Autowired
    AuditRecorder recorder;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    final UUID entityId = Ids.newV7();

    @Test
    void recordsARequestActorWithTheirUserIdAndRequestId() {
        var user = Ids.newV7();

        AuditContext.runAs(
                AuditActor.user(user),
                "req-1",
                () -> tx.executeWithoutResult(status -> recorder.record(
                        "workout_session", entityId, AuditAction.UPDATE, "{\"note\": \"a\"}", "{\"note\": \"b\"}")));

        var row = onlyRowFor(entityId);
        assertThat(row.get("actor")).isEqualTo("user:" + user);
        assertThat(row.get("user_id")).isEqualTo(user);
        assertThat(row.get("request_id")).isEqualTo("req-1");
        assertThat(row.get("entity_type")).isEqualTo("workout_session");
        assertThat(row.get("action")).isEqualTo("UPDATE");
        assertThat(row.get("before")).isEqualTo("{\"note\": \"a\"}");
        assertThat(row.get("after")).isEqualTo("{\"note\": \"b\"}");
        assertThat(row.get("occurred_at")).isEqualTo(Timestamp.from(NOW));
        assertThat(row.get("id"))
                .isInstanceOfSatisfying(
                        UUID.class, id -> assertThat(id.version()).isEqualTo(7));
    }

    @Test
    void recordsASystemActorWithoutAUserId() {
        AuditContext.runAs(
                AuditActor.system("auto-finish"),
                "run-1",
                () -> tx.executeWithoutResult(
                        status -> recorder.record("workout_session", entityId, AuditAction.UPDATE, "{}", "{}")));

        var row = onlyRowFor(entityId);
        assertThat(row.get("actor")).isEqualTo("system:auto-finish");
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("request_id")).isEqualTo("run-1");
    }

    @Test
    void recordsAnAnonymousActor() {
        AuditContext.runAs(
                AuditActor.anonymous(),
                "req-2",
                () -> tx.executeWithoutResult(
                        status -> recorder.record("user", entityId, AuditAction.CREATE, null, "{}")));

        var row = onlyRowFor(entityId);
        assertThat(row.get("actor")).isEqualTo("anonymous");
        assertThat(row.get("user_id")).isNull();
    }

    @Test
    void storesAMissingBeforeOrAfterStateAsNull() {
        AuditContext.runAs(
                AuditActor.anonymous(),
                "req-3",
                () -> tx.executeWithoutResult(status -> {
                    recorder.record("set", entityId, AuditAction.CREATE, null, "{\"reps\": 8}");
                    recorder.record("set", entityId, AuditAction.DELETE, "{\"reps\": 8}", null);
                }));

        var rows = jdbc.queryForList(
                "select action, before::text, after::text from audit_log where entity_id = ? order by action",
                entityId);
        assertThat(rows)
                .extracting(r -> r.get("action"), r -> r.get("before"), r -> r.get("after"))
                .containsExactly(tuple("CREATE", null, "{\"reps\": 8}"), tuple("DELETE", "{\"reps\": 8}", null));
    }

    @Test
    void failsAndRollsBackTheTransactionWhenNoContextIsBound() {
        var otherEntity = Ids.newV7();
        // A binding that has ended must not leak into later work on the same thread.
        AuditContext.runAs(AuditActor.anonymous(), "req-4", () -> {});

        assertThatIllegalStateException()
                .isThrownBy(() -> tx.executeWithoutResult(status -> {
                    jdbc.update(
                            "insert into audit_log (id, actor, entity_type, entity_id, action, request_id, occurred_at)"
                                    + " values (?, 'anonymous', 'x', ?, 'CREATE', 'r', now())",
                            Ids.newV7(),
                            otherEntity);
                    recorder.record("set", entityId, AuditAction.CREATE, null, "{}");
                }));

        assertThat(rowsFor(entityId)).isEmpty();
        assertThat(rowsFor(otherEntity)).isEmpty();
    }

    @Test
    void rollingBackTheBusinessTransactionAlsoRollsBackTheAuditRow() {
        AuditContext.runAs(
                AuditActor.anonymous(),
                "req-5",
                () -> tx.executeWithoutResult(status -> {
                    recorder.record("set", entityId, AuditAction.CREATE, null, "{}");
                    status.setRollbackOnly();
                }));

        assertThat(rowsFor(entityId)).isEmpty();
    }

    @Test
    void refusesToWriteOutsideATransaction() {
        AuditContext.runAs(
                AuditActor.anonymous(),
                "req-6",
                () -> assertThatExceptionOfType(IllegalTransactionStateException.class)
                        .isThrownBy(() -> recorder.record("set", entityId, AuditAction.CREATE, null, "{}")));

        assertThat(rowsFor(entityId)).isEmpty();
    }

    @Test
    void auditRowsCannotBeChangedOrDeleted() {
        AuditContext.runAs(
                AuditActor.anonymous(),
                "req-7",
                () -> tx.executeWithoutResult(
                        status -> recorder.record("set", entityId, AuditAction.CREATE, null, "{}")));

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbc.update("update audit_log set actor = 'x' where entity_id = ?", entityId));
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbc.update("delete from audit_log where entity_id = ?", entityId));
        assertThat(rowsFor(entityId)).hasSize(1);
    }

    private Map<String, @Nullable Object> onlyRowFor(UUID id) {
        var rows = jdbc.queryForList(
                "select id, user_id, actor, entity_type, action, before::text, after::text, request_id, occurred_at"
                        + " from audit_log where entity_id = ?",
                id);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private List<Map<String, @Nullable Object>> rowsFor(UUID id) {
        return jdbc.queryForList("select id from audit_log where entity_id = ?", id);
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
