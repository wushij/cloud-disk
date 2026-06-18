package com.clouddisk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** TRANSCODE_DONE / SHARE_EXPIRED / TEAM_INVITED 等 */
    private String type;
    private String title;
    private String content;
    /** 关联资源 ID（文件ID、团队ID 等） */
    private String refId;
    private Integer isRead;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
