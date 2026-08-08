package com.clouddisk.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer status;
    private String role;
    private Long storageQuota;
    private Long storageUsed;
    private Boolean defaultPassword;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
