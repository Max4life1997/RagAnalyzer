-- Таблица для хранения изображений, извлечённых из PDF

CREATE TABLE IF NOT EXISTS document_images (
    id           UUID        NOT NULL PRIMARY KEY,
    document_id  UUID        NOT NULL REFERENCES documents(id),
    page_number  INTEGER     NOT NULL,
    image_index  INTEGER     NOT NULL,
    image_path   TEXT        NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_document_images_document_page
    ON document_images(document_id, page_number);
