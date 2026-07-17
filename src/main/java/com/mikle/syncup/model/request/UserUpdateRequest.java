package com.mikle.syncup.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Fields that a user is allowed to update through the public profile API.
 */
@Data
public class UserUpdateRequest implements Serializable {

    @NotNull
    @Positive
    private Long id;

    @Size(max = 64)
    private String username;

    @Size(max = 1024)
    private String avatarUrl;

    private Integer gender;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;

    @Email(message = "邮箱格式错误")
    @Size(max = 254)
    private String email;

    @Size(max = 1024)
    private String tags;

    @Size(max = 500)
    private String profile;

    private static final long serialVersionUID = 1L;
}
