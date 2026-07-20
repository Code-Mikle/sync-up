package com.mikle.syncup.ai.model.schema;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProfileExtraction implements Serializable {

    private List<String> interests = new ArrayList<>();

    private List<String> activityTypes = new ArrayList<>();

    private List<String> availableTimes = new ArrayList<>();

    private String city;

    private List<String> districts = new ArrayList<>();

    private String socialPreference;

    private List<String> skillLevels = new ArrayList<>();

    private String budgetPreference;

    private List<String> candidateTags = new ArrayList<>();

    private Double confidence;

    private String sourceText;

    private String modelVersion;

    private static final long serialVersionUID = 1L;
}

