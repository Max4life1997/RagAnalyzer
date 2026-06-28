package ru.max.raganalyzer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "length", nullable = false)
    private int length;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DocumentChunkEntity() {
    }

    public DocumentChunkEntity(
            DocumentEntity document,
            int chunkIndex,
            String content,
            int length,
            int pageNumber
    ) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.length = length;
        this.pageNumber = pageNumber;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public DocumentEntity getDocument() {
        return document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public int getLength() {
        return length;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}