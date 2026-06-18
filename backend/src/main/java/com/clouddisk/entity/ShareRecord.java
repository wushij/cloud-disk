package com.clouddisk.entity;



import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;



import java.time.LocalDateTime;



@Data

@TableName("tb_share")

public class ShareRecord {

    @TableId(type = IdType.AUTO)

    private Long id;

    private Long userId;

    /** FILE 分享时使用 */

    private Long fileId;

    /** FILE / FOLDER */

    private String shareType;

    /** FOLDER 分享时使用 */

    private Long folderId;

    private String shareCode;

    private String extractCode;

    private LocalDateTime expireTime;

    private Integer viewCount;

    private Integer downloadCount;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)

    private LocalDateTime createTime;



    public boolean isFolderShare() {

        return "FOLDER".equalsIgnoreCase(shareType);

    }

}

