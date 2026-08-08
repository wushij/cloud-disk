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
public class ShareVO {
    private Long id;
    private String shareCode;
    private String shareType;
    private String extractCode;
    private LocalDateTime expireTime;
    private Integer viewCount;
    private Integer downloadCount;
    private Integer status;
    private String shareUrl;
    private LocalDateTime createdAt;
    private Long folderId;
    private Long fileId;
    private String fileName;
}
