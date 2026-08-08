package com.clouddisk.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FileVO {
    private Long id;
    private Long userId;
    private Long folderId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String fileMd5;
    private String thumbnailPath;
    private String posterPath;
    private String transcodePath;
    private String transcodeStatus;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
