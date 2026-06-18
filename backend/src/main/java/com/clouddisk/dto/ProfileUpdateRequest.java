package com.clouddisk.dto;

import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
}
