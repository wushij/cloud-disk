package com.clouddisk.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenVO {
    private String token;
    private String username;
    private String nickname;
    private String role;
    private Long userId;
    private Boolean defaultPassword;
}
