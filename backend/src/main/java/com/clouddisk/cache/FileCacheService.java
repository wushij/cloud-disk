package com.clouddisk.cache;

import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 文件元数据缓存服务，减少 DB 查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCacheService {

    private static final long FILE_TTL = 300;        // 文件记录缓存 5 分钟
    private static final long MD5_TTL = 86400 * 7;   // MD5 缓存 7 天

    private final CacheService cacheService;
    private final FileMapper fileMapper;
    private final ObjectMapper objectMapper;

    /**
     * 根据文件 ID 获取 FileRecord（优先缓存）
     */
    public FileRecord getById(Long fileId) {
        if (fileId == null) return null;
        String key = "file:" + fileId;
        String cached = cacheService.get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, FileRecord.class);
            } catch (JsonProcessingException e) {
                cacheService.delete(key);
            }
        }
        FileRecord record = fileMapper.selectById(fileId);
        if (record != null) {
            cacheFile(record);
        }
        return record;
    }

    /**
     * 根据 MD5 查找文件（秒传场景）
     */
    public FileRecord getByMd5(String md5) {
        if (!StringUtils.hasText(md5)) return null;

        String idKey = "md5:id:" + md5;
        String cachedId = cacheService.get(idKey);
        if (cachedId != null) {
            try {
                FileRecord cached = getById(Long.parseLong(cachedId));
                if (cached != null && md5.equals(cached.getFileMd5()) && cached.getStatus() == 1) {
                    return cached;
                }
                cacheService.delete(idKey);
            } catch (NumberFormatException e) {
                cacheService.delete(idKey);
            }
        }

        FileRecord record = fileMapper.selectOne(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getFileMd5, md5)
                .eq(FileRecord::getStatus, 1)
                .last("LIMIT 1"));

        if (record != null) {
            cacheFile(record);
            cacheService.set(idKey, String.valueOf(record.getId()), MD5_TTL);
        }
        return record;
    }

    /**
     * 缓存文件记录
     */
    public void cacheFile(FileRecord record) {
        if (record == null || record.getId() == null) return;
        try {
            String key = "file:" + record.getId();
            cacheService.set(key, objectMapper.writeValueAsString(record), FILE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("文件缓存序列化失败 fileId={}", record.getId());
        }
    }

    /**
     * 清除文件缓存
     */
    public void evict(Long fileId) {
        if (fileId != null) {
            cacheService.delete("file:" + fileId);
        }
    }

    /**
     * 清除文件及 MD5 缓存
     */
    public void evictWithMd5(Long fileId, String md5) {
        evict(fileId);
        if (StringUtils.hasText(md5)) {
            cacheService.delete("md5:id:" + md5);
            cacheService.delete("md5:" + md5);
        }
    }
}
