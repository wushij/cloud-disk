package com.clouddisk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailSendCodeRequest {
    @NotBlank(message = "邮箱地址不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    /** 业务场景: login / register / resetpwd */
    private String scene;
}
