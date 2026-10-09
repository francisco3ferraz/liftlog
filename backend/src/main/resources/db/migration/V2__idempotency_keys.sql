-- One row per Idempotency-Key a user has sent. status and response_body stay null while the first request is
-- running, then hold the response to replay. Rows expire after 7 days, so purges scan by created_at.
CREATE TABLE idempotency_keys (
    user_id       uuid        NOT NULL,
    key           uuid        NOT NULL,
    request_hash  text        NOT NULL,
    status        smallint,
    response_body jsonb,
    created_at    timestamptz NOT NULL,
    PRIMARY KEY (user_id, key),
    CHECK (status IS NOT NULL OR response_body IS NULL)
);

CREATE INDEX idempotency_keys_created_at_idx ON idempotency_keys (created_at);
