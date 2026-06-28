-- Добавляем номер страницы к чанкам для отображения источника в чате

ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS page_number INTEGER NOT NULL DEFAULT 1;
