package com.clouddisk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_upload_session")
public class UploadSession {
    @TableId
    private String id;
    private Long userId;
    private String fileName;
    private String fileMd5;
    private Long folderId;
    private Long totalSize;
    private Integer chunkSize;
    private Integer totalChunks;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private LocalDateTime expiresAt;
}
