package com.clouddisk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_file_chunk")
public class FileChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uploadId;
    private Integer chunkNo;
    private Integer chunkSize;
    private Integer uploadStatus;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
