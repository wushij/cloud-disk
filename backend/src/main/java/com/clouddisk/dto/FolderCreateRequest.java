package com.clouddisk.dto;

import lombok.Data;

@Data
public class FolderCreateRequest {
    private Long parentId = 0L;
    private String folderName;
}
