package com.clouddisk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_team_member")
public class TeamMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spaceId;
    private Long userId;
  /** OWNER / ADMIN / MEMBER / VIEWER */
    private String role;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinTime;
}
