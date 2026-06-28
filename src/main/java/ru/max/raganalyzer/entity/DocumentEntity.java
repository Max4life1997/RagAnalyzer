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

    @Column(name = "chunks_count", nullable = false)
    private int chunksCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DocumentEntity() {
    }

    public DocumentEntity(
            String originalFileName,
            String storedPath,
            long sizeBytes,
            int textLength
    ) {
        this.originalFileName = originalFileName;
        this.storedPath = storedPath;
        this.sizeBytes = sizeBytes;
        this.textLength = textLength;
        this.chunksCount = 0;
        this.status = DocumentStatus.PROCESSING;
        this.errorMessage = null;
        this.createdAt = LocalDateTime.now();
    }

    public void updateTextLength(int textLength) {
        this.textLength = textLength;
    }

    public void markIndexed(int chunksCount) {
        this.status = DocumentStatus.INDEXED;
        this.chunksCount = chunksCount;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
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

    public int getChunksCount() {
        return chunksCount;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
