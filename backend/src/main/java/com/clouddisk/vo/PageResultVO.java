package com.clouddisk.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResultVO<T> {
    private List<T> content;
    private Long totalElements;
    private Integer page;
    private Integer size;
    private Map<String, Object> teamAccess;
}
