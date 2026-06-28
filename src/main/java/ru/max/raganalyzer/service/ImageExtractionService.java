package ru.max.raganalyzer.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import ru.max.raganalyzer.config.FileStorageProperties;
import ru.max.raganalyzer.entity.DocumentEntity;
import ru.max.raganalyzer.entity.DocumentImageEntity;
import ru.max.raganalyzer.repository.DocumentImageRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ImageExtractionService {

    // Минимальный размер изображения — игнорируем иконки и декоративные элементы
    private static final int MIN_IMAGE_WIDTH  = 80;
    private static final int MIN_IMAGE_HEIGHT = 80;

    private final Path imagesBaseDir;
    private final DocumentImageRepository documentImageRepository;

    public ImageExtractionService(
            FileStorageProperties properties,
            DocumentImageRepository documentImageRepository
    ) {
        this.imagesBaseDir = Path.of(properties.getDocumentsDir())
                .getParent()
                .resolve("images");
        this.documentImageRepository = documentImageRepository;
    }

    public List<DocumentImageEntity> extractAndSave(Path pdfPath, DocumentEntity document) {
        String fileName = pdfPath.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".pdf")) {
            return List.of();
        }

        Path docImagesDir = imagesBaseDir.resolve(document.getId().toString());

        try {
            Files.createDirectories(docImagesDir);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать папку для изображений", e);
        }

        List<DocumentImageEntity> saved = new ArrayList<>();

        try (PDDocument pdf = Loader.loadPDF(pdfPath.toAbsolutePath().toFile())) {
            int pageNumber = 1;

            for (PDPage page : pdf.getPages()) {
                List<BufferedImage> pageImages = extractImagesFromPage(page);

                int imageIndex = 0;
                for (BufferedImage image : pageImages) {
                    String fileName2 = "%d_%d.png".formatted(pageNumber, imageIndex);
                    Path imagePath = docImagesDir.resolve(fileName2);

                    ImageIO.write(image, "PNG", imagePath.toFile());

                    DocumentImageEntity entity = new DocumentImageEntity(
                            document, pageNumber, imageIndex, imagePath.toString()
                    );
                    saved.add(documentImageRepository.save(entity));
                    imageIndex++;
                }

                pageNumber++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при извлечении изображений из PDF: " + pdfPath, e);
        }

        return saved;
    }

    public void deleteImages(UUID documentId) {
        documentImageRepository.deleteByDocumentId(documentId);

        Path docImagesDir = imagesBaseDir.resolve(documentId.toString());
        if (Files.exists(docImagesDir)) {
            try {
                Files.walk(docImagesDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
    }

    private List<BufferedImage> extractImagesFromPage(PDPage page) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        collectImages(page.getResources(), images);
        return images;
    }

    private void collectImages(PDResources resources, List<BufferedImage> images) throws IOException {
        if (resources == null) return;

        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);

            if (xObject instanceof PDImageXObject imageXObject) {
                BufferedImage image = imageXObject.getImage();

                if (image != null
                        && image.getWidth()  >= MIN_IMAGE_WIDTH
                        && image.getHeight() >= MIN_IMAGE_HEIGHT) {
                    images.add(image);
                }
            }
        }
    }
}
