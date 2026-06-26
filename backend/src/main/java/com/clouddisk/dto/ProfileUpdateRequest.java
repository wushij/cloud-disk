package com.clouddisk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    @Size(min = 1, max = 64, message = "昵称长度 1-64 位")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 位")
    private String email;

    @Pattern(regexp = "^[0-9+\\-]{6,32}$", message = "手机号格式不正确")
    private String phone;

}
