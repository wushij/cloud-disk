package com.clouddisk.service;

import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
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
}
