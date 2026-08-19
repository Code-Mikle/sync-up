package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.schema.GeneratedUserProfile;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class UserProfileTextAssembler {

    public static final String INTEREST_HEADER = "【兴趣与活动偏好】";
    public static final String SOCIAL_HEADER = "【社交与性格倾向】";
    public static final String PARTNER_HEADER = "【搭子匹配偏好】";
    public static final String CONSTRAINT_HEADER = "【活动约束与习惯】";
    public static final String INTERACTION_HEADER = "【AI 交互偏好】";

    private static final int MAX_SECTION_LENGTH = 600;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");

    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(token|api[_-]?key|password|密码)\\s*[:：=]\\s*\\S+");

    private static final List<String> HEADERS = List.of(
            INTEREST_HEADER, SOCIAL_HEADER, PARTNER_HEADER, CONSTRAINT_HEADER, INTERACTION_HEADER);

    public GeneratedUserProfile parse(String rawText) {
        String text = StringUtils.defaultString(rawText).trim()
                .replace("```text", "")
                .replace("```markdown", "")
                .replace("```", "")
                .trim();
        int[] positions = HEADERS.stream().mapToInt(text::indexOf).toArray();
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] < 0 || (i > 0 && positions[i] <= positions[i - 1])) {
                throw new IllegalArgumentException("generated profile does not contain five ordered sections");
            }
        }
        return new GeneratedUserProfile(
                section(text, positions, 0),
                section(text, positions, 1),
                section(text, positions, 2),
                section(text, positions, 3),
                section(text, positions, 4));
    }

    public String renderFull(GeneratedUserProfile profile) {
        validate(profile);
        return String.join("\n\n",
                block(INTEREST_HEADER, profile.getInterestAndActivityPreference()),
                block(SOCIAL_HEADER, profile.getSocialAndPersonalityTendency()),
                block(PARTNER_HEADER, profile.getPartnerMatchingPreference()),
                block(CONSTRAINT_HEADER, profile.getActivityConstraintsAndHabits()),
                block(INTERACTION_HEADER, profile.getAiInteractionPreference()));
    }

    public String renderMatch(GeneratedUserProfile profile) {
        validate(profile);
        return String.join("\n\n",
                block(INTEREST_HEADER, profile.getInterestAndActivityPreference()),
                block(SOCIAL_HEADER, profile.getSocialAndPersonalityTendency()),
                block(PARTNER_HEADER, profile.getPartnerMatchingPreference()),
                block(CONSTRAINT_HEADER, profile.getActivityConstraintsAndHabits()));
    }

    public String renderInteraction(GeneratedUserProfile profile) {
        validate(profile);
        return block(INTERACTION_HEADER, profile.getAiInteractionPreference());
    }

    private String section(String text, int[] positions, int index) {
        int start = positions[index] + HEADERS.get(index).length();
        int end = index + 1 < positions.length ? positions[index + 1] : text.length();
        return normalizeSection(text.substring(start, end));
    }

    private void validate(GeneratedUserProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("generated profile is required");
        }
        profile.setInterestAndActivityPreference(normalizeSection(profile.getInterestAndActivityPreference()));
        profile.setSocialAndPersonalityTendency(normalizeSection(profile.getSocialAndPersonalityTendency()));
        profile.setPartnerMatchingPreference(normalizeSection(profile.getPartnerMatchingPreference()));
        profile.setActivityConstraintsAndHabits(normalizeSection(profile.getActivityConstraintsAndHabits()));
        profile.setAiInteractionPreference(normalizeSection(profile.getAiInteractionPreference()));
    }

    private String normalizeSection(String value) {
        String normalized = StringUtils.defaultString(value).trim();
        if (StringUtils.isBlank(normalized)) {
            throw new IllegalArgumentException("generated profile section must not be blank");
        }
        if (normalized.length() > MAX_SECTION_LENGTH) {
            throw new IllegalArgumentException("generated profile section is too long");
        }
        if (EMAIL_PATTERN.matcher(normalized).find()
                || PHONE_PATTERN.matcher(normalized).find()
                || SECRET_PATTERN.matcher(normalized).find()) {
            throw new IllegalArgumentException("generated profile contains sensitive contact or credential data");
        }
        return normalized;
    }

    private String block(String header, String content) {
        return header + "\n" + content;
    }
}
