package com.clouddisk.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamSpaceVO {
    private Long id;
    private String name;
    private Long ownerId;
    private Long rootFolderId;
    private Long maxSize;
    private Integer status;
    private String avatar;
    private String role;
    private Integer memberCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
