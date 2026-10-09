package io.github.francisco3ferraz.liftlog.shared;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Clock;

/** Stamps {@link BaseEntity} timestamps. Hibernate creates it through Spring, so the {@link Clock} is injected. */
class EntityTimestamps {

    private final Clock clock;

    EntityTimestamps(Clock clock) {
        this.clock = clock;
    }

    @PrePersist
    void onInsert(BaseEntity entity) {
        entity.stampCreated(BaseEntity.now(clock));
    }

    @PreUpdate
    void onUpdate(BaseEntity entity) {
        entity.stampUpdated(BaseEntity.now(clock));
    }
}
