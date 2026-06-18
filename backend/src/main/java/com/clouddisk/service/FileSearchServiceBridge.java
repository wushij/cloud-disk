package com.clouddisk.service;

import com.clouddisk.entity.FileRecord;
import com.clouddisk.search.FileSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 解耦 FolderService 与 FileSearchService 的循环依赖。
 */
@Component
public class FileSearchServiceBridge {

    @Autowired(required = false)
    private FileSearchService fileSearchService;

    public void onFileRecycled(FileRecord file) {
        if (fileSearchService != null) {
            fileSearchService.indexFile(file);
        }
    }
}
