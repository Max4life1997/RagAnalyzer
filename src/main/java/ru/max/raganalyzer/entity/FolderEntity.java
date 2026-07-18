package ru.max.raganalyzer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "folders")
public class FolderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "parent_folder_id")
    private UUID parentFolderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected FolderEntity() {
    }

    public FolderEntity(String name, UUID parentFolderId, UUID userId) {
        this.name = name;
        this.parentFolderId = parentFolderId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getParentFolderId() { return parentFolderId; }
    public void setParentFolderId(UUID parentFolderId) { this.parentFolderId = parentFolderId; }
    public UUID getUserId() { return userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
