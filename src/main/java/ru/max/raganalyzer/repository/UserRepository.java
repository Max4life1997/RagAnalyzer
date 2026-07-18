package ru.max.raganalyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.max.raganalyzer.entity.UserEntity;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
}
