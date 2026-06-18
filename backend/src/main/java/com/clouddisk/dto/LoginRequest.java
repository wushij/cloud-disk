package com.clouddisk.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    /** 算术验证码 ID（失败次数达到阈值后必填） */
    private String captchaId;
    /** 算术验证码答案 */
    private String captchaAnswer;
}
