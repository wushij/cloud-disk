package com.clouddisk.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaMessage implements Serializable {
    private Long fileId;
    private Long userId;
    private String storagePath;
    private String fileName;
    /** MIME type，例如 image/png、video/mp4 */
    private String fileType;
}
