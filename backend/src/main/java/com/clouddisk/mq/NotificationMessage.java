package com.clouddisk.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String refId;
}
