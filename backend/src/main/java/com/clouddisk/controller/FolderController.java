package com.clouddisk.controller;

import com.clouddisk.dto.FolderCreateRequest;
import com.clouddisk.dto.MoveRequest;
import com.clouddisk.dto.RenameRequest;
import com.clouddisk.entity.Folder;
import com.clouddisk.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping("/tree")
    public List<Map<String, Object>> tree() {
        return folderService.tree();
    }

    @PostMapping
    public Folder create(@RequestBody FolderCreateRequest req) {
        return folderService.create(req);
    }

    @PutMapping("/{id}/rename")
    public Folder rename(@PathVariable Long id, @RequestBody RenameRequest req) {
        return folderService.rename(id, req);
    }

    @PutMapping("/{id}/move")
    public Folder move(@PathVariable Long id, @RequestBody MoveRequest req) {
        return folderService.move(id, req);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        folderService.deleteToRecycle(id);
        return Map.of("message", "已移入回收站");
    }
}
