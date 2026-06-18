package com.clouddisk.controller;

import com.clouddisk.service.RecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recycle")
@RequiredArgsConstructor
public class RecycleController {

    private final RecycleService recycleService;

    @GetMapping
    public List<Map<String, Object>> list() {
        return recycleService.list();
    }

    @PostMapping("/restore/file/{id}")
    public Map<String, String> restoreFile(@PathVariable Long id) {
        recycleService.restoreFile(id);
        return Map.of("message", "已恢复");
    }

    @PostMapping("/restore/folder/{id}")
    public Map<String, String> restoreFolder(@PathVariable Long id) {
        recycleService.restoreFolder(id);
        return Map.of("message", "已恢复");
    }

    @DeleteMapping("/file/{id}")
    public Map<String, String> deleteFile(@PathVariable Long id) {
        recycleService.permanentDeleteFile(id);
        return Map.of("message", "已永久删除");
    }

    @DeleteMapping("/folder/{id}")
    public Map<String, String> deleteFolder(@PathVariable Long id) {
        recycleService.permanentDeleteFolder(id);
        return Map.of("message", "已永久删除");
    }

    @DeleteMapping("/clear")
    public Map<String, String> clear() {
        recycleService.clearAll();
        return Map.of("message", "回收站已清空");
    }
}
