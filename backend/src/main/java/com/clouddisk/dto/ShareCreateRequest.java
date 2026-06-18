package com.clouddisk.dto;



import lombok.Data;



@Data

public class ShareCreateRequest {

    private Long fileId;

    private Long folderId;

    /** FILE 或 FOLDER，可省略由 fileId/folderId 推断 */

    private String shareType;

    private String extractCode;

    private Integer expireHours;

}

