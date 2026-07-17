package com.mikle.syncup.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户加入队伍请求体
 */
@Data
public class TeamJoinRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * id
     */
    @NotNull
    @Positive
    private Long teamId;

    /**
     * 密码
     */
    @Size(max = 32)
    private String password;
}
