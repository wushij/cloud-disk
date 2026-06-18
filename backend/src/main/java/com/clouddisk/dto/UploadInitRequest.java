package com.clouddisk.dto;

import lombok.Data;

@Data
public class UploadInitRequest {
    private String fileName;
    private Long totalSize;
    private Integer chunkSize;
    private String fileMd5;
    private Long folderId = 0L;
}
