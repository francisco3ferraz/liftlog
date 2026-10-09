package io.github.francisco3ferraz.liftlog.shared;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Entity with an assigned UUID id, optimistic locking and timestamps taken from the injected {@link Clock}.
 * Implements {@link Persistable} so saving a new entity with a client-supplied id INSERTs instead of merging.
 */
@MappedSuperclass
@EntityListeners(EntityTimestamps.class)
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Version
    private long version;

    @SuppressWarnings("NullAway.Init") // stamped by EntityTimestamps before the first insert
    private Instant createdAt;

    @SuppressWarnings("NullAway.Init") // stamped by EntityTimestamps before the first insert
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    @SuppressWarnings("NullAway.Init") // JPA populates the fields
    protected BaseEntity() {}

    protected BaseEntity(UUID id) {
        this.id = id;
    }

    /** {@code clock}'s instant at the microsecond precision Postgres stores, so it survives a reload unchanged. */
    static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    void stampCreated(Instant now) {
        createdAt = now;
        updatedAt = now;
    }

    void stampUpdated(Instant now) {
        updatedAt = now;
    }
}
