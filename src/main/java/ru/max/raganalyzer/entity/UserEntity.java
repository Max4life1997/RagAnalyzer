package ru.max.raganalyzer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected UserEntity() {}

    public UserEntity(String email, String passwordHash) {
        this.email        = email;
        this.passwordHash = passwordHash;
        this.createdAt    = LocalDateTime.now();
    }

    public UUID getId()            { return id; }
    public String getEmail()       { return email; }
    public String getPasswordHash(){ return passwordHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
