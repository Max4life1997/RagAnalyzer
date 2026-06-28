package ru.max.raganalyzer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "stored_path", nullable = false)
    private String storedPath;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "text_length", nullable = false)
    private int textLength;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DocumentEntity() {
    }

    public DocumentEntity(
            String originalFileName,
            String storedPath,
            long sizeBytes,
            int textLength,
            String status
    ) {
        this.originalFileName = originalFileName;
        this.storedPath = storedPath;
        this.sizeBytes = sizeBytes;
        this.textLength = textLength;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getTextLength() {
        return textLength;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}