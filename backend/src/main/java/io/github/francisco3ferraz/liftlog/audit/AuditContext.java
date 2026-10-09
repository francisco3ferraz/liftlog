package io.github.francisco3ferraz.liftlog.audit;

import java.util.Optional;

/**
 * The actor and request id that audit records are attributed to. Whatever starts a unit of work binds them: the
 * request filter for HTTP requests, and each scheduled job, startup task and asynchronous event listener around its
 * own body. A binding lasts exactly as long as the operation it wraps and is not inherited by other threads.
 */
public final class AuditContext {

    private static final ScopedValue<Binding> CURRENT = ScopedValue.newInstance();

    private AuditContext() {}

    /** Runs {@code op} with audit records attributed to {@code actor} and {@code requestId}. */
    public static void runAs(AuditActor actor, String requestId, Runnable op) {
        if (requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        ScopedValue.where(CURRENT, new Binding(actor, requestId)).run(op);
    }

    static Optional<Binding> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    record Binding(AuditActor actor, String requestId) {}
}
