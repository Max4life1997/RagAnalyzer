package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.max.raganalyzer.dto.FolderDto;
import ru.max.raganalyzer.entity.FolderEntity;
import ru.max.raganalyzer.repository.FolderRepository;
import ru.max.raganalyzer.security.SecurityUtils;

import java.util.List;
import java.util.UUID;

@Service
public class FolderService {

    private final FolderRepository folderRepository;

    public FolderService(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    @Transactional
    public FolderDto createFolder(String name, UUID parentFolderId) {
        UUID userId = SecurityUtils.currentUserId();

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название папки не может быть пустым");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("Слишком длинное название папки");
        }
        if (parentFolderId != null && !folderRepository.existsByIdAndUserId(parentFolderId, userId)) {
            throw new IllegalArgumentException("Родительская папка не найдена");
        }

        FolderEntity folder = new FolderEntity(name.trim(), parentFolderId, userId);
        return toDto(folderRepository.save(folder));
    }

    @Transactional(readOnly = true)
    public List<FolderDto> getAllFolders() {
        UUID userId = SecurityUtils.currentUserId();
        return folderRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public FolderDto renameFolder(UUID folderId, String newName) {
        UUID userId = SecurityUtils.currentUserId();

        FolderEntity folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Папка не найдена"));

        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Название папки не может быть пустым");
        }

        folder.setName(newName.trim());
        return toDto(folderRepository.save(folder));
    }

    @Transactional
    public void deleteFolder(UUID folderId) {
        UUID userId = SecurityUtils.currentUserId();

        if (!folderRepository.existsByIdAndUserId(folderId, userId)) {
            throw new IllegalArgumentException("Папка не найдена");
        }

        // ON DELETE CASCADE на parent_folder_id удалит вложенные папки,
        // ON DELETE SET NULL на documents.folder_id переместит документы в корень —
        // оба эффекта настроены на уровне БД через внешние ключи (см. миграцию V5)
        folderRepository.deleteById(folderId);
    }

    private FolderDto toDto(FolderEntity folder) {
        return new FolderDto(folder.getId(), folder.getName(), folder.getParentFolderId(), folder.getCreatedAt());
    }
}
