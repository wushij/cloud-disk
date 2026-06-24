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

import java.util.concurrent.ThreadLocalRandom;

/**
 * 文件元数据缓存服务，减少 DB 查询
 * <ul>
 *   <li>防穿透：查不到数据时缓存空标记 "NULL"，短 TTL</li>
 *   <li>防雪崩：TTL 随机抖动，避免同一时刻集中过期</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCacheService {

    private static final String NULL_MARKER = "NULL";
    private static final long FILE_TTL_BASE = 300;
    private static final long FILE_TTL_JITTER = 60;
    private static final long MD5_TTL_BASE = 86400 * 7;
    private static final long MD5_TTL_JITTER = 3600;
    private static final long NULL_TTL = 60;

    private final CacheService cacheService;
    private final FileMapper fileMapper;
    private final ObjectMapper objectMapper;

    public FileRecord getById(Long fileId) {
        if (fileId == null) return null;
        String key = "file:" + fileId;
        String cached = cacheService.get(key);
        if (cached != null) {
            if (NULL_MARKER.equals(cached)) {
                return null;
            }
            try {
                return objectMapper.readValue(cached, FileRecord.class);
            } catch (JsonProcessingException e) {
                cacheService.delete(key);
            }
        }
        FileRecord record = fileMapper.selectById(fileId);
        if (record != null) {
            cacheFile(record);
        } else {
            cacheService.set(key, NULL_MARKER, NULL_TTL);
        }
        return record;
    }

    /**
     * 按用户 + MD5 查找可秒传的文件（仅当前用户已上传且状态正常的记录）。
     */
    public FileRecord getByMd5(long userId, String md5) {
        if (!StringUtils.hasText(md5)) {
            return null;
        }

        String idKey = md5CacheKey(userId, md5);
        String cachedId = cacheService.get(idKey);
        if (cachedId != null) {
            if (NULL_MARKER.equals(cachedId)) {
                return null;
            }
            try {
                FileRecord cached = getById(Long.parseLong(cachedId));
                if (cached != null
                        && cached.getUserId() != null && cached.getUserId().equals(userId)
                        && md5.equals(cached.getFileMd5())
                        && cached.getStatus() == 1) {
                    return cached;
                }
                cacheService.delete(idKey);
            } catch (NumberFormatException e) {
                cacheService.delete(idKey);
            }
        }

        FileRecord record = fileMapper.selectOne(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getUserId, userId)
                .eq(FileRecord::getFileMd5, md5)
                .eq(FileRecord::getStatus, 1)
                .orderByDesc(FileRecord::getId)
                .last("LIMIT 1"));

        if (record != null) {
            cacheFile(record);
            cacheService.set(idKey, String.valueOf(record.getId()), jitteredTtl(MD5_TTL_BASE, MD5_TTL_JITTER));
        } else {
            cacheService.set(idKey, NULL_MARKER, NULL_TTL);
        }
        return record;
    }

    public static String md5CacheKey(long userId, String md5) {
        return "md5:id:" + userId + ":" + md5;
    }

    public static String md5PathCacheKey(long userId, String md5) {
        return "md5:" + userId + ":" + md5;
    }

    public void cacheFile(FileRecord record) {
        if (record == null || record.getId() == null) return;
        try {
            String key = "file:" + record.getId();
            cacheService.set(key, objectMapper.writeValueAsString(record), jitteredTtl(FILE_TTL_BASE, FILE_TTL_JITTER));
        } catch (JsonProcessingException e) {
            log.warn("文件缓存序列化失败 fileId={}", record.getId());
        }
    }

    public void evict(Long fileId) {
        if (fileId != null) {
            cacheService.delete("file:" + fileId);
        }
    }

    public void evictWithMd5(Long fileId, long userId, String md5) {
        evict(fileId);
        if (StringUtils.hasText(md5)) {
            cacheService.delete(md5CacheKey(userId, md5));
            cacheService.delete(md5PathCacheKey(userId, md5));
        }
    }

    private long jitteredTtl(long base, long jitter) {
        return base + ThreadLocalRandom.current().nextLong(jitter);
    }
}
