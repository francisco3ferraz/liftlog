-- Spring Modulith's event publication registry (its v2 PostgreSQL schema). A row is written when an event is
-- published to a transactional listener and completed once the listener succeeds; incomplete rows are
-- resubmitted on restart, and completed rows are purged after 7 days.
CREATE TABLE event_publication (
    id                     uuid        NOT NULL,
    listener_id            text        NOT NULL,
    event_type             text        NOT NULL,
    serialized_event       text        NOT NULL,
    publication_date       timestamptz NOT NULL,
    completion_date        timestamptz,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamptz,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
