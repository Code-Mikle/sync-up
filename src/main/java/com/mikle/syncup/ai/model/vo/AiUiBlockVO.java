package com.mikle.syncup.ai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiUiBlockVO implements Serializable {

    public static final String TEAM_LIST = "team_list";
    public static final String USER_RECOMMENDATIONS = "user_recommendations";
    public static final String PROFILE_CARD = "profile_card";
    public static final String TEAM_DRAFT_CONFIRMATION = "team_draft_confirmation";
    public static final String TEAM_DELETE_CONFIRMATION = "team_delete_confirmation";

    private String type;

    private String variant;

    private Object data;

    public static AiUiBlockVO of(String type, Object data) {
        return new AiUiBlockVO(type, null, data);
    }

    public static AiUiBlockVO of(String type, String variant, Object data) {
        return new AiUiBlockVO(type, variant, data);
    }

    private static final long serialVersionUID = 1L;
}
