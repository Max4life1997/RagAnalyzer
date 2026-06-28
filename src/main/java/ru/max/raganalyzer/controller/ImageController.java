package ru.max.raganalyzer.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.max.raganalyzer.config.FileStorageProperties;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final Path imagesBaseDir;

    public ImageController(FileStorageProperties properties) {
        this.imagesBaseDir = Path.of(properties.getDocumentsDir())
                .getParent()
                .resolve("images");
    }

    @GetMapping("/{documentId}/{fileName}")
    public ResponseEntity<Resource> getImage(
            @PathVariable String documentId,
            @PathVariable String fileName
    ) {
        // Защита от path traversal — имя файла не должно содержать /  или ..
        if (fileName.contains("/") || fileName.contains("..") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        Path imagePath = imagesBaseDir.resolve(documentId).resolve(fileName);
        Resource resource = new FileSystemResource(imagePath);

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }
}
