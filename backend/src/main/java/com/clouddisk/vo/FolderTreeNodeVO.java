package com.clouddisk.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderTreeNodeVO {
    private Long id;
    private String label;
    private Long parentId;
    private List<FolderTreeNodeVO> children;
}
