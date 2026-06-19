package com.clouddisk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_team_invitation")
public class TeamInvitation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spaceId;
    private Long inviterId;
    private Long inviteeId;
    /** OWNER / ADMIN / MEMBER */
    private String role;
    /** PENDING / ACCEPTED / REJECTED */
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
