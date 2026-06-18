package com.clouddisk.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.dto.Md5CheckRequest;
import com.clouddisk.dto.UploadInitRequest;
import com.clouddisk.entity.FileChunk;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.UploadSession;
import com.clouddisk.mapper.FileChunkMapper;
import com.clouddisk.mapper.UploadSessionMapper;
import com.clouddisk.security.VirusScanService;
import com.clouddisk.storage.StorageService;
import com.clouddisk.util.FileValidator;
import com.clouddisk.websocket.UploadProgressHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final UploadSessionMapper sessionMapper;
    private final FileChunkMapper chunkMapper;
    private final FileService fileService;
    private final StorageService storageService;
    private final CloudDiskProperties properties;
    private final FileValidator fileValidator;
    private final UploadProgressHandler progressHandler;
    private final VirusScanService virusScanService;

    public Map<String, Object> checkMd5(Md5CheckRequest req) {
        AuthService.currentUserId();
        Map<String, Object> result = new HashMap<>();
        if (req.getFileMd5() == null || req.getFileMd5().isBlank()) {
            result.put("exists", false);
            return result;
        }
        fileValidator.validate(req.getFileName(), req.getFileSize());
        FileRecord existing = fileService.findByMd5(req.getFileMd5());
        if (existing != null) {
            FileRecord created = fileService.instantUpload(
                    req.getFileMd5(), req.getFileName(), req.getFileSize(),
                    req.getFolderId() != null ? req.getFolderId() : 0L);
            result.put("exists", true);
            result.put("instant", true);
            result.put("fileId", created.getId());
            return result;
        }
        result.put("exists", false);
        return result;
    }

    public Map<String, Object> init(UploadInitRequest req) {
        long userId = AuthService.currentUserId();
        if (req.getFileName() == null || req.getTotalSize() == null || req.getTotalSize() <= 0) {
            throw new BusinessException("参数不完整");
        }
        fileValidator.validate(req.getFileName(), req.getTotalSize());

        int chunkSize = req.getChunkSize() != null ? req.getChunkSize() : properties.getChunk().getDefaultSize();
        chunkSize = Math.min(chunkSize, properties.getChunk().getMaxSize());
        int totalChunks = (int) Math.ceil(req.getTotalSize() * 1.0 / chunkSize);
        if (totalChunks > properties.getChunk().getMaxChunks()) {
            throw new BusinessException("文件过大，分片数超限");
        }

        String uploadId = IdUtil.simpleUUID();
        UploadSession session = new UploadSession();
        session.setId(uploadId);
        session.setUserId(userId);
        session.setFileName(req.getFileName());
        session.setFileMd5(req.getFileMd5());
        session.setFolderId(req.getFolderId() != null ? req.getFolderId() : 0L);
        session.setTotalSize(req.getTotalSize());
        session.setChunkSize(chunkSize);
        session.setTotalChunks(totalChunks);
        session.setStatus("PENDING");
        session.setExpiresAt(LocalDateTime.now().plusHours(properties.getChunk().getSessionExpireHours()));
        sessionMapper.insert(session);

        List<Integer> uploaded = getUploadedChunks(uploadId);

        Map<String, Object> result = new HashMap<>();
        result.put("uploadId", uploadId);
        result.put("chunkSize", chunkSize);
        result.put("totalChunks", totalChunks);
        result.put("uploadedChunks", uploaded);
        return result;
    }

    public void uploadChunk(String uploadId, int chunkIndex, MultipartFile file) throws Exception {
        long userId = AuthService.currentUserId();
        UploadSession session = getSession(uploadId, userId);
        if (file.isEmpty()) throw new BusinessException("分片为空");
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new BusinessException("分片索引无效");
        }

        String path = fileService.chunkStoragePath(uploadId, chunkIndex);
        storageService.store(file.getInputStream(), path, file.getSize(), "application/octet-stream");

        FileChunk existing = chunkMapper.selectOne(new LambdaQueryWrapper<FileChunk>()
                .eq(FileChunk::getUploadId, uploadId)
                .eq(FileChunk::getChunkNo, chunkIndex));
        if (existing == null) {
            FileChunk chunk = new FileChunk();
            chunk.setUploadId(uploadId);
            chunk.setChunkNo(chunkIndex);
            chunk.setChunkSize((int) file.getSize());
            chunk.setUploadStatus(1);
            chunkMapper.insert(chunk);
        }

        long done = chunkMapper.selectCount(new LambdaQueryWrapper<FileChunk>().eq(FileChunk::getUploadId, uploadId));
        double progress = done * 1.0 / session.getTotalChunks();
        progressHandler.sendProgress(userId, uploadId, session.getFileName(), progress, "uploading");
    }

    public FileRecord merge(String uploadId, String mimeType) throws Exception {
        long userId = AuthService.currentUserId();
        UploadSession session = getSession(uploadId, userId);
        List<FileChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<FileChunk>()
                .eq(FileChunk::getUploadId, uploadId)
                .orderByAsc(FileChunk::getChunkNo));
        if (chunks.size() < session.getTotalChunks()) {
            throw new BusinessException("分片未全部上传，当前 " + chunks.size() + "/" + session.getTotalChunks());
        }

        String storagePath = fileService.buildStoragePath(userId, session.getFileName());
        List<String> parts = IntStream.range(0, session.getTotalChunks())
                .mapToObj(i -> fileService.chunkStoragePath(uploadId, i))
                .collect(Collectors.toList());
        storageService.mergeParts(storagePath, parts);

        try (var in = storageService.loadAsResource(storagePath).getInputStream()) {
            virusScanService.scan(in, session.getFileName(), session.getTotalSize());
        }

        FileRecord record = fileService.createRecord(
                userId, session.getFolderId(), session.getFileName(),
                session.getTotalSize(), mimeType, session.getFileMd5(), storagePath);

        session.setStatus("MERGED");
        sessionMapper.updateById(session);
        chunkMapper.delete(new LambdaQueryWrapper<FileChunk>().eq(FileChunk::getUploadId, uploadId));
        progressHandler.sendProgress(userId, uploadId, session.getFileName(), 1.0, "done");
        return record;
    }

    public List<Integer> resume(String uploadId) {
        long userId = AuthService.currentUserId();
        getSession(uploadId, userId);
        return getUploadedChunks(uploadId);
    }

    private List<Integer> getUploadedChunks(String uploadId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<FileChunk>()
                        .eq(FileChunk::getUploadId, uploadId)
                        .orderByAsc(FileChunk::getChunkNo))
                .stream().map(FileChunk::getChunkNo).collect(Collectors.toList());
    }

    private UploadSession getSession(String uploadId, long userId) {
        UploadSession session = sessionMapper.selectById(uploadId);
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException("上传会话不存在");
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("上传会话已过期");
        }
        return session;
    }
}
