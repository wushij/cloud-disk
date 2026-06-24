package com.clouddisk.service;

import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageReferenceServiceTest {

    @Mock private FileMapper fileMapper;
    @Mock private StorageService storageService;
    @InjectMocks private StorageReferenceService storageReferenceService;

    @Test
    void deletePhysicalArtifacts_sharedStoragePath_doesNotDeleteObject() {
        FileRecord file = new FileRecord();
        file.setId(1L);
        file.setStoragePath("users/1/a.bin");
        file.setThumbnailPath("thumb/1.jpg");

        when(fileMapper.selectCount(any())).thenReturn(1L);

        storageReferenceService.deletePhysicalArtifacts(file, 1L);

        verify(storageService, never()).delete(any());
    }

    @Test
    void deletePhysicalArtifacts_lastReference_deletesAllArtifacts() {
        FileRecord file = new FileRecord();
        file.setId(2L);
        file.setStoragePath("users/2/a.bin");
        file.setThumbnailPath("thumb/2.jpg");
        file.setPosterPath("poster/2.jpg");
        file.setTranscodePath("transcode/2.mp4");

        when(fileMapper.selectCount(any())).thenReturn(0L);

        storageReferenceService.deletePhysicalArtifacts(file, 2L);

        verify(storageService).delete("users/2/a.bin");
        verify(storageService).delete("thumb/2.jpg");
        verify(storageService).delete("poster/2.jpg");
        verify(storageService).delete("transcode/2.mp4");
    }

    @Test
    void deletePhysicalArtifacts_samePosterAndThumbnail_deletesOnce() {
        FileRecord file = new FileRecord();
        file.setId(3L);
        file.setStoragePath("users/3/a.bin");
        file.setThumbnailPath("thumb/shared.jpg");
        file.setPosterPath("thumb/shared.jpg");

        when(fileMapper.selectCount(any())).thenReturn(0L);

        storageReferenceService.deletePhysicalArtifacts(file, 3L);

        verify(storageService).delete("users/3/a.bin");
        verify(storageService, times(1)).delete("thumb/shared.jpg");
    }

    @Test
    void countReferencesToPath_checksAllPathColumns() {
        when(fileMapper.selectCount(any())).thenReturn(2L);

        long count = storageReferenceService.countReferencesToPath("path/x", 9L);

        assertEquals(2L, count);
        verify(fileMapper, times(1)).selectCount(any());
    }
}
