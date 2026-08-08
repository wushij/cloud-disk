package com.clouddisk.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageUsageVO {
    private Long usedBytes;
    private Long quotaBytes;
    private String usedFormatted;
    private String quotaFormatted;
    private Double usedPercent;
}
