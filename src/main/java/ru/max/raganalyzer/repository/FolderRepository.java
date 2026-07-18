package ru.max.raganalyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.max.raganalyzer.entity.FolderEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<FolderEntity, UUID> {
    List<FolderEntity> findByUserId(UUID userId);
    Optional<FolderEntity> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByIdAndUserId(UUID id, UUID userId);
}
