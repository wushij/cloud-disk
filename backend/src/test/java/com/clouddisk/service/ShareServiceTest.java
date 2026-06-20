package com.clouddisk.service;

import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.ShareRecord;
import com.clouddisk.mapper.ShareMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock private ShareMapper shareMapper;
    @Mock private com.clouddisk.mapper.FileMapper fileMapper;
    @Mock private com.clouddisk.mapper.FolderMapper folderMapper;
    @Mock private FileService fileService;
    @Mock private FolderService folderService;
    @Mock private CacheService cacheService;
    @Mock private CloudDiskProperties properties;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @InjectMocks private ShareService shareService;

    @Test
    void verifyExtractCode_wrongCode_throws() {
        ShareRecord share = new ShareRecord();
        share.setShareCode("abc12345");
        share.setExtractCode("1234");
        when(properties.getRateLimit()).thenReturn(new CloudDiskProperties.RateLimit());
        when(cacheService.increment(any(), anyLong())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> shareService.verifyExtractCode(share, "0000"));
        assertEquals("提取码错误", ex.getMessage());
    }

    @Test
    void verifyExtractCode_correctCode_passes() {
        ShareRecord share = new ShareRecord();
        share.setShareCode("abc12345");
        share.setExtractCode("1234");

        assertDoesNotThrow(() -> shareService.verifyExtractCode(share, "1234"));
    }

    @Test
    void getValidShare_expiredShare_throws() {
        ShareRecord share = new ShareRecord();
        share.setShareCode("expired1");
        share.setStatus(1);
        share.setExpireTime(LocalDateTime.now().minusHours(1));

        when(cacheService.get(any())).thenReturn(null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> shareService.getValidShare("expired1"));
        assertEquals("分享已过期", ex.getMessage());
        verify(shareMapper).updateById(share);
        assertEquals(0, share.getStatus());
    }

    @Test
    void verifyAndGetFile_duplicateRequests_countOnceWithinDedupeWindow() {
        ShareRecord share = new ShareRecord();
        share.setId(10L);
        share.setShareCode("abc12345");
        share.setShareType("FILE");
        share.setFileId(100L);
        share.setStatus(1);
        share.setDownloadCount(0);

        FileRecord file = new FileRecord();
        file.setId(100L);
        file.setStatus(1);

        when(cacheService.get(any())).thenReturn(null);
        when(shareMapper.selectOne(any())).thenReturn(share);
        when(fileService.getForDownload(100L)).thenReturn(file);
        when(cacheService.increment(any(), eq(120L))).thenReturn(1L, 2L, 3L);

        shareService.verifyAndGetFile("abc12345", null);
        shareService.verifyAndGetFile("abc12345", null);
        shareService.verifyAndGetFile("abc12345", null);

        verify(shareMapper, times(1)).update(any(), any());
        assertEquals(1, share.getDownloadCount());
    }
}
