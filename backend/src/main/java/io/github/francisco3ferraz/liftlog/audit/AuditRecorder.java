package io.github.francisco3ferraz.liftlog.audit;

import io.github.francisco3ferraz.liftlog.shared.Ids;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends audit records. Each record joins the caller's transaction, so it commits or rolls back with the change it
 * describes, and is attributed to the actor and request id bound in {@link AuditContext}. Writes through JDBC rather
 * than JPA so it is safe to call while Hibernate is flushing.
 */
@Component
public class AuditRecorder {

    private final JdbcClient jdbc;
    private final Clock clock;

    AuditRecorder(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Records that {@code action} happened to the entity. {@code before} and {@code after} are its JSON state; {@code
     * before} is null for {@link AuditAction#CREATE} and {@code after} may be null when there is no state left.
     *
     * @throws IllegalStateException if no {@link AuditContext} is bound, which also rolls back the caller's
     *     transaction
     * @throws org.springframework.transaction.IllegalTransactionStateException if called outside a transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            String entityType, UUID entityId, AuditAction action, @Nullable String before, @Nullable String after) {
        var context = AuditContext.current()
                .orElseThrow(() -> new IllegalStateException(
                        "No AuditContext bound for " + action + " of " + entityType + " " + entityId));
        jdbc.sql("""
                        insert into audit_log
                            (id, user_id, actor, entity_type, entity_id, action, before, after, request_id, occurred_at)
                        values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)""")
                .param(Ids.newV7())
                .param(context.actor().userId().orElse(null))
                .param(context.actor().value())
                .param(entityType)
                .param(entityId)
                .param(action.name())
                .param(before)
                .param(after)
                .param(context.requestId())
                .param(clock.instant().atOffset(ZoneOffset.UTC))
                .update();
    }
}
