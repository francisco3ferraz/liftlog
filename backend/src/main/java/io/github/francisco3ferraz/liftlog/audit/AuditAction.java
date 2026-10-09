package io.github.francisco3ferraz.liftlog.audit;

/** What happened to an entity. A soft delete is {@link #DELETE} and undoing it is {@link #RESTORE}. */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    RESTORE
}
