package com.clouddisk.service;

import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.dto.UploadInitRequest;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.UploadSession;
import com.clouddisk.mapper.FileChunkMapper;
import com.clouddisk.mapper.UploadSessionMapper;
import com.clouddisk.security.VirusScanService;
import com.clouddisk.storage.StorageService;
import com.clouddisk.util.FileValidator;
import com.clouddisk.websocket.UploadProgressHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock private UploadSessionMapper sessionMapper;
    @Mock private FileChunkMapper chunkMapper;
    @Mock private FileService fileService;
    @Mock private StorageService storageService;
    @Mock private FileValidator fileValidator;
    @Mock private UploadProgressHandler progressHandler;
    @Mock private VirusScanService virusScanService;
    @InjectMocks private UploadService uploadService;

    private CloudDiskProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CloudDiskProperties();
        properties.getChunk().setDefaultSize(8 * 1024 * 1024);
        properties.getChunk().setMaxSize(64 * 1024 * 1024);
        properties.getChunk().setMaxChunks(10);
        properties.getChunk().setSessionExpireHours(24);
        uploadService = new UploadService(
                sessionMapper, chunkMapper, fileService, storageService,
                properties, fileValidator, progressHandler, virusScanService);
    }

    @Test
    void checkMd5_blankMd5_returnsNotExists() {
        try (MockedStatic<AuthService> auth = mockStatic(AuthService.class)) {
            auth.when(AuthService::currentUserId).thenReturn(1L);
            var req = new com.clouddisk.dto.Md5CheckRequest();
            req.setFileMd5("  ");
            var result = uploadService.checkMd5(req);
            assertEquals(false, result.get("exists"));
            verify(fileService, never()).findByMd5(anyLong(), anyString());
        }
    }

    @Test
    void checkMd5_usesCurrentUserScope() {
        try (MockedStatic<AuthService> auth = mockStatic(AuthService.class)) {
            auth.when(AuthService::currentUserId).thenReturn(7L);
            var req = new com.clouddisk.dto.Md5CheckRequest();
            req.setFileMd5("md5-1");
            req.setFileName("doc.pdf");
            req.setFileSize(1024L);
            FileRecord existing = new FileRecord();
            existing.setId(100L);
            when(fileService.findByMd5(7L, "md5-1")).thenReturn(existing);
            when(fileService.instantUpload("md5-1", "doc.pdf", 1024L, 0L)).thenReturn(existing);

            var result = uploadService.checkMd5(req);

            assertEquals(true, result.get("exists"));
            assertEquals(true, result.get("instant"));
            verify(fileService).findByMd5(7L, "md5-1");
        }
    }

    @Test
    void init_incompleteParams_throws() {
        try (MockedStatic<AuthService> auth = mockStatic(AuthService.class)) {
            auth.when(AuthService::currentUserId).thenReturn(1L);
            UploadInitRequest req = new UploadInitRequest();
            req.setFileName("a.zip");
            assertThrows(BusinessException.class, () -> uploadService.init(req));
        }
    }

    @Test
    void init_tooManyChunks_throws() {
        try (MockedStatic<AuthService> auth = mockStatic(AuthService.class)) {
            auth.when(AuthService::currentUserId).thenReturn(1L);
            UploadInitRequest req = new UploadInitRequest();
            req.setFileName("big.zip");
            req.setTotalSize(100L * 1024 * 1024);
            req.setChunkSize(1024 * 1024);
            BusinessException ex = assertThrows(BusinessException.class, () -> uploadService.init(req));
            assertTrue(ex.getMessage().contains("分片数超限"));
        }
    }

    @Test
    void merge_incompleteChunks_throws() {
        try (MockedStatic<AuthService> auth = mockStatic(AuthService.class)) {
            auth.when(AuthService::currentUserId).thenReturn(1L);
            UploadSession session = new UploadSession();
            session.setId("upload-1");
            session.setUserId(1L);
            session.setFileName("a.zip");
            session.setTotalSize(2048L);
            session.setTotalChunks(2);
            session.setExpiresAt(LocalDateTime.now().plusHours(1));
            when(sessionMapper.selectById("upload-1")).thenReturn(session);
            when(chunkMapper.selectList(any())).thenReturn(java.util.List.of());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> uploadService.merge("upload-1", "application/zip"));
            assertTrue(ex.getMessage().contains("分片未全部上传"));
        }
    }
}