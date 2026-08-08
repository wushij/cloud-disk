package com.clouddisk.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityPublicConfigVO {
    private Boolean timestampEnabled;
    private Boolean nonceEnabled;
    private Boolean sm3SignEnabled;
    private Boolean sm2SignEnabled;
    private Boolean sm4EncryptEnabled;
}
