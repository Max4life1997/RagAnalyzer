package ru.max.raganalyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.max.raganalyzer.entity.DocumentChunkEntity;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {
    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndex(UUID documentId);
}