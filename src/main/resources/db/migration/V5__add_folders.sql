CREATE TABLE IF NOT EXISTS folders (
    id               UUID         NOT NULL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    parent_folder_id UUID         REFERENCES folders(id) ON DELETE CASCADE,
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at       TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_folders_user_id ON folders(user_id);
CREATE INDEX IF NOT EXISTS idx_folders_parent_id ON folders(parent_folder_id);

-- ON DELETE SET NULL: при удалении папки документы внутри неё не теряются,
-- а просто переезжают в корень (folder_id = null)
ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS folder_id UUID REFERENCES folders(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_documents_folder_id ON documents(folder_id);
