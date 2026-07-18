package ru.max.raganalyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.max.raganalyzer.entity.DocumentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    List<DocumentEntity> findByUserId(UUID userId);

    // Получить документ только если он принадлежит этому пользователю
    Optional<DocumentEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}
