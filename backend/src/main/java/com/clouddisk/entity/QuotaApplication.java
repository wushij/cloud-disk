package com.clouddisk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_quota_application")
public class QuotaApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long currentQuota;
    private Long applyQuota;
    private String reason;
    private String status;
    private String approvalOpinion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
