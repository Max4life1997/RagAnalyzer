package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.max.raganalyzer.config.FileStorageProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path documentsDir;

    public FileStorageService(FileStorageProperties properties) {
        this.documentsDir = Path.of(properties.getDocumentsDir());
        createDocumentsDirectory();
    }

    public Path save(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        String safeFileName = buildSafeFileName(originalFileName);

        Path targetPath = documentsDir.resolve(safeFileName);

        try {
            file.transferTo(targetPath);
            return targetPath;
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить файл: " + originalFileName, e);
        }
    }

    private String buildSafeFileName(String originalFileName) {
        String extension = getExtension(originalFileName);
        return UUID.randomUUID() + extension;
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }

        return fileName.substring(fileName.lastIndexOf("."));
    }

    private void createDocumentsDirectory() {
        try {
            Files.createDirectories(documentsDir);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать папку для документов: " + documentsDir, e);
        }
    }
}