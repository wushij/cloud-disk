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
public class QuotaApplicationVO {
    private Long id;
    private Long userId;
    private String username;
    private Long currentQuota;
    private Long applyQuota;
    private String reason;
    private String status;
    private String approvalOpinion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
