package ru.max.raganalyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.max.raganalyzer.entity.DocumentImageEntity;

import java.util.List;
import java.util.UUID;

public interface DocumentImageRepository extends JpaRepository<DocumentImageEntity, UUID> {
    List<DocumentImageEntity> findByDocumentIdAndPageNumberIn(UUID documentId, List<Integer> pageNumbers);
    void deleteByDocumentId(UUID documentId);
}
