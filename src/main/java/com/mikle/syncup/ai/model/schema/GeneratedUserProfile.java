package com.mikle.syncup.ai.model.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedUserProfile {

    private String interestAndActivityPreference;

    private String socialAndPersonalityTendency;

    private String partnerMatchingPreference;

    private String activityConstraintsAndHabits;

    private String aiInteractionPreference;
}
