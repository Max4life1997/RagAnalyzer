package ru.max.raganalyzer.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "document_images")
public class DocumentImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "image_index", nullable = false)
    private int imageIndex;

    @Column(name = "image_path", nullable = false)
    private String imagePath;

    protected DocumentImageEntity() {}

    public DocumentImageEntity(DocumentEntity document, int pageNumber, int imageIndex, String imagePath) {
        this.document = document;
        this.pageNumber = pageNumber;
        this.imageIndex = imageIndex;
        this.imagePath = imagePath;
    }

    public UUID getId() { return id; }
    public DocumentEntity getDocument() { return document; }
    public int getPageNumber() { return pageNumber; }
    public int getImageIndex() { return imageIndex; }
    public String getImagePath() { return imagePath; }
}
