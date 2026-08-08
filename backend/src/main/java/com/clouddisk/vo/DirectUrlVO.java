package com.clouddisk.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectUrlVO {
    private String url;
    private Integer expireSeconds;
    private String storageType;
    private String bucket;
    private Boolean proxy;
}
