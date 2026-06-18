package com.clouddisk.dto;

import lombok.Data;

@Data
public class Md5CheckRequest {
    private String fileMd5;
    private String fileName;
    private Long fileSize;
    private Long folderId = 0L;
}
