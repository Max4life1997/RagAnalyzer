-- Начальная схема базы данных RagAnalyzer

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documents (
    id            UUID         NOT NULL PRIMARY KEY,
    original_file_name TEXT    NOT NULL,
    stored_path   TEXT         NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    text_length   INTEGER      NOT NULL,
    chunks_count  INTEGER      NOT NULL DEFAULT 0,
    status        VARCHAR(50)  NOT NULL,
    error_message TEXT,
    created_at    TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS document_chunks (
    id            UUID         NOT NULL PRIMARY KEY,
    document_id   UUID         NOT NULL REFERENCES documents(id),
    chunk_index   INTEGER      NOT NULL,
    content       TEXT         NOT NULL,
    length        INTEGER      NOT NULL,
    embedding     vector(768),
    created_at    TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_document_chunks_document_id
    ON document_chunks(document_id);
