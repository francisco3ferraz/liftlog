-- One row per change to user data, written in the same transaction as the change. actor is user:<uuid>,
-- system:<name> or anonymous; user_id repeats the uuid of a user actor so a user's history can be queried by index.
-- user_id has no foreign key because rows outlive what they describe.
CREATE TABLE audit_log (
    id          uuid        PRIMARY KEY,
    user_id     uuid,
    actor       text        NOT NULL,
    entity_type text        NOT NULL,
    entity_id   uuid        NOT NULL,
    action      text        NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'RESTORE')),
    before      jsonb,
    after       jsonb,
    request_id  text        NOT NULL,
    occurred_at timestamptz NOT NULL
);

CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id, occurred_at);

-- Append-only: rows can be inserted, never changed or deleted.
CREATE FUNCTION audit_log_reject_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_change();
