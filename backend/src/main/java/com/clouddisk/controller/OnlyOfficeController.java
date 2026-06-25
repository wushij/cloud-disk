package com.clouddisk.controller;

import com.clouddisk.onlyoffice.OnlyOfficeService;
import com.clouddisk.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OnlyOfficeController {

    private final OnlyOfficeService onlyOfficeService;
    private final AuthService authService;

    @GetMapping("/api/files/{id}/onlyoffice")
    public Map<String, Object> editorConfig(
            @PathVariable Long id,
            @RequestParam(required = false) String mode) {
        long userId = AuthService.currentUserId();
        var me = authService.me();
        String username = String.valueOf(me.getOrDefault("nickname", me.get("username")));
        return onlyOfficeService.buildEditorConfig(id, userId, username, mode);
    }

    @GetMapping("/api/onlyoffice/files/{id}/download")
    public ResponseEntity<Resource> downloadForServer(
            @PathVariable Long id,
            @RequestParam String ooToken) {
        Resource resource = onlyOfficeService.loadForDocumentServer(id, ooToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/api/onlyoffice/callback")
    public Map<String, Object> callback(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return onlyOfficeService.handleCallback(body, authorization);
    }
}
