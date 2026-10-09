package io.github.francisco3ferraz.liftlog.shared;

import java.time.Duration;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes completed event publications once they are too old to help diagnose a listener. */
@Component
class EventPublicationPurgeJob {

    static final Duration RETENTION = Duration.ofDays(7);

    private final CompletedEventPublications completed;

    EventPublicationPurgeJob(CompletedEventPublications completed) {
        this.completed = completed;
    }

    @Scheduled(cron = "0 30 * * * *", zone = "UTC")
    void purge() {
        completed.deletePublicationsOlderThan(RETENTION);
    }
}
