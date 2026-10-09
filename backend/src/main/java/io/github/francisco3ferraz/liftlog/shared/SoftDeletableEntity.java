package io.github.francisco3ferraz.liftlog.shared;

import jakarta.persistence.MappedSuperclass;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@link BaseEntity} that is moved to the trash instead of being deleted, and can be restored from it. */
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

    private @Nullable Instant deletedAt;

    protected SoftDeletableEntity() {}

    protected SoftDeletableEntity(UUID id) {
        super(id);
    }

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete(Clock clock) {
        deletedAt = now(clock);
    }

    public void restore() {
        deletedAt = null;
    }
}
