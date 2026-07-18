CREATE TABLE IF NOT EXISTS users (
    id            UUID         NOT NULL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL
);

-- Добавляем user_id к документам (nullable для совместимости с уже существующими)
ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;
