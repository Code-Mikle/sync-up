package com.mikle.syncup.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户退出队伍请求体
 */
@Data
public class TeamQuitRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * id
     */
    @NotNull
    @Positive
    private Long teamId;

}
