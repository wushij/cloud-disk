package com.clouddisk.controller;

import com.clouddisk.dto.FolderCreateRequest;
import com.clouddisk.dto.MoveRequest;
import com.clouddisk.dto.RenameRequest;
import com.clouddisk.service.FolderService;
import com.clouddisk.util.VOMapper;
import com.clouddisk.vo.BreadcrumbVO;
import com.clouddisk.vo.FolderTreeNodeVO;
import com.clouddisk.vo.FolderVO;
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
    public List<FolderTreeNodeVO> tree() {
        return folderService.tree();
    }

    @PostMapping
    public FolderVO create(@RequestBody FolderCreateRequest req) {
        return VOMapper.toFolderVO(folderService.create(req));
    }

    @PutMapping("/{id}/rename")
    public FolderVO rename(@PathVariable Long id, @RequestBody RenameRequest req) {
        return VOMapper.toFolderVO(folderService.rename(id, req));
    }

    @PutMapping("/{id}/move")
    public FolderVO move(@PathVariable Long id, @RequestBody MoveRequest req) {
        return VOMapper.toFolderVO(folderService.move(id, req));
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        folderService.deleteToRecycle(id);
        return Map.of("message", "已移入回收站");
    }

    @GetMapping("/{id}/breadcrumbs")
    public List<BreadcrumbVO> getBreadcrumbs(
            @PathVariable Long id,
            @RequestParam(value = "full", required = false, defaultValue = "false") boolean full) {
        return folderService.getBreadcrumbs(id, com.clouddisk.service.AuthService.currentUserId(), full);
    }
}
