package com.clouddisk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_file")
public class FileRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long folderId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String fileMd5;
    private String storagePath;
    private String bucketName;
    private String thumbnailPath;
    private String posterPath;
    private String transcodePath;
    /** 视频转码状态: NONE/PENDING/PROCESSING/DONE/FAILED */
    private String transcodeStatus;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
