package com.clouddisk.cache;

import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileCacheServiceTest {

    @Mock private CacheService cacheService;
    @Mock private FileMapper fileMapper;
    private FileCacheService fileCacheService;

    @BeforeEach
    void setUp() {
        fileCacheService = new FileCacheService(cacheService, fileMapper, new ObjectMapper());
    }

    @Test
    void getByMd5_scopedToUser_doesNotReturnOtherUsersFile() {
        when(cacheService.get(FileCacheService.md5CacheKey(2L, "deadbeef"))).thenReturn(null);
        when(fileMapper.selectOne(any())).thenReturn(null);

        FileRecord result = fileCacheService.getByMd5(2L, "deadbeef");

        assertNull(result);
        verify(fileMapper).selectOne(any());
        verify(cacheService).set(eq(FileCacheService.md5CacheKey(2L, "deadbeef")), eq("NULL"), eq(60L));
    }

    @Test
    void getByMd5_returnsMatchingUserFile() {
        FileRecord record = new FileRecord();
        record.setId(10L);
        record.setUserId(5L);
        record.setFileMd5("abc123");
        record.setStatus(1);

        when(cacheService.get(FileCacheService.md5CacheKey(5L, "abc123"))).thenReturn(null);
        when(fileMapper.selectOne(any())).thenReturn(record);

        FileRecord result = fileCacheService.getByMd5(5L, "abc123");

        assertNotNull(result);
        assertEquals(10L, result.getId());
        verify(cacheService).set(eq(FileCacheService.md5CacheKey(5L, "abc123")), eq("10"), anyLong());
    }

    @Test
    void getByMd5_cachedIdFromOtherUser_isRejected() {
        FileRecord otherUser = new FileRecord();
        otherUser.setId(99L);
        otherUser.setUserId(1L);
        otherUser.setFileMd5("shared");
        otherUser.setStatus(1);

        when(cacheService.get(FileCacheService.md5CacheKey(2L, "shared"))).thenReturn("99");
        when(cacheService.get("file:99")).thenReturn(null);
        when(fileMapper.selectById(99L)).thenReturn(otherUser);
        when(fileMapper.selectOne(any())).thenReturn(null);

        FileRecord result = fileCacheService.getByMd5(2L, "shared");

        assertNull(result);
        verify(cacheService).delete(FileCacheService.md5CacheKey(2L, "shared"));
    }
}
