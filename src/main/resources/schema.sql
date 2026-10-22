CREATE TABLE IF NOT EXISTS documents (
    document_id VARCHAR(128) PRIMARY KEY,
    raw_text CLOB NOT NULL,
    channel VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    invalidated BOOLEAN NOT NULL DEFAULT FALSE,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    vector_bytes VARBINARY(1024),
    embedding_version VARCHAR(128),
    vector_completed_at TIMESTAMP WITH TIME ZONE,
    hit_count BIGINT NOT NULL DEFAULT 0 CHECK (hit_count >= 0),
    CONSTRAINT document_invalidation_state CHECK (
        (invalidated = FALSE AND invalidated_at IS NULL) OR
        (invalidated = TRUE AND invalidated_at IS NOT NULL)),
    CONSTRAINT document_vector_state CHECK (
        (vector_bytes IS NULL AND embedding_version IS NULL AND vector_completed_at IS NULL) OR
        (vector_bytes IS NOT NULL AND OCTET_LENGTH(vector_bytes) = 1024 AND
         embedding_version IS NOT NULL AND vector_completed_at IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS embedding_tasks (
    task_id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(128) NOT NULL UNIQUE REFERENCES documents(document_id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    embedding_version VARCHAR(128) NOT NULL,
    run_token VARCHAR(36),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(64),
    error_message VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_tasks_dispatch ON embedding_tasks(status, submitted_at, task_id);
CREATE INDEX IF NOT EXISTS idx_documents_filter ON documents(invalidated, channel, submitted_at, document_id);

