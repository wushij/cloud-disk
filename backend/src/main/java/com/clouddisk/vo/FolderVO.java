package com.clouddisk.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FolderVO {
    private Long id;
    private Long userId;
    private Long parentId;
    private String folderName;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
