package ru.max.raganalyzer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.max.raganalyzer.dto.CreateFolderRequest;
import ru.max.raganalyzer.dto.FolderDto;
import ru.max.raganalyzer.dto.RenameFolderRequest;
import ru.max.raganalyzer.service.FolderService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public List<FolderDto> getAllFolders() {
        return folderService.getAllFolders();
    }

    @PostMapping
    public FolderDto createFolder(@RequestBody CreateFolderRequest request) {
        return folderService.createFolder(request.name(), request.parentFolderId());
    }

    @PutMapping("/{folderId}")
    public FolderDto renameFolder(@PathVariable UUID folderId, @RequestBody RenameFolderRequest request) {
        return folderService.renameFolder(folderId, request.name());
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(@PathVariable UUID folderId) {
        folderService.deleteFolder(folderId);
        return ResponseEntity.noContent().build();
    }
}
