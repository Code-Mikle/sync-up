package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.model.schema.ProfileExtraction;
import com.mikle.syncup.ai.service.ProfileExtractionParser;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedProfileExtractionParser implements ProfileExtractionParser {

    private static final String MODEL_VERSION = "rule-profile-extractor-v1";

    private static final List<String> ACTIVITY_TYPES = List.of("羽毛球", "篮球", "足球", "跑步", "健身", "徒步", "骑行", "游泳", "桌游", "摄影", "自习", "编程");

    private static final List<String> CITIES = List.of("北京", "上海", "广州", "深圳", "杭州", "成都", "西安", "南京", "武汉", "重庆", "长沙", "郑州");

    private static final List<String> DISTRICTS = List.of("朝阳", "海淀", "浦东", "宝安", "南山", "雁塔", "碑林", "高新", "武侯", "江宁");

    private static final List<String> AVAILABLE_TIMES = List.of("周一", "周二", "周三", "周四", "周五", "周末", "晚上", "上午", "下午", "工作日");

    private static final List<String> SKILL_LEVELS = List.of("新手", "入门", "初级", "中等", "进阶", "高级");

    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[,，、\\s]+");

    @Override
    public ProfileExtraction parse(String sourceText) {
        String normalizedText = StringUtils.defaultString(sourceText).trim();
        ProfileExtraction extraction = new ProfileExtraction();
        extraction.setSourceText(normalizedText);
        extraction.setModelVersion(MODEL_VERSION);
        if (StringUtils.isBlank(normalizedText)) {
            extraction.setConfidence(0.0);
            return extraction;
        }

        extraction.setActivityTypes(findMatches(normalizedText, ACTIVITY_TYPES));
        extraction.setInterests(new java.util.ArrayList<>(extraction.getActivityTypes()));
        extraction.setAvailableTimes(findMatches(normalizedText, AVAILABLE_TIMES));
        extraction.setDistricts(findMatches(normalizedText, DISTRICTS));
        extraction.setSkillLevels(findMatches(normalizedText, SKILL_LEVELS));
        extraction.setCity(findFirst(normalizedText, CITIES));
        extraction.setSocialPreference(resolveSocialPreference(normalizedText));
        extraction.setBudgetPreference(resolveBudgetPreference(normalizedText));
        extraction.setCandidateTags(resolveCandidateTags(normalizedText, extraction));
        extraction.setConfidence(resolveConfidence(extraction));
        return extraction;
    }

    private List<String> findMatches(String text, List<String> dictionary) {
        return dictionary.stream()
                .filter(text::contains)
                .distinct()
                .toList();
    }

    private String findFirst(String text, List<String> dictionary) {
        return dictionary.stream().filter(text::contains).findFirst().orElse(null);
    }

    private String resolveSocialPreference(String text) {
        if (text.contains("安静") || text.contains("少人") || text.contains("小队")) {
            return "小队安静";
        }
        if (text.contains("热闹") || text.contains("多人") || text.contains("社交")) {
            return "多人社交";
        }
        return null;
    }

    private String resolveBudgetPreference(String text) {
        Matcher matcher = Pattern.compile("(\\d{1,5})\\s*(元|块|以内|以下)?").matcher(text);
        if (matcher.find() && (text.contains("预算") || text.contains("人均") || text.contains("以内"))) {
            return matcher.group(1) + "以内";
        }
        if (text.contains("免费") || text.contains("低成本")) {
            return "低预算";
        }
        return null;
    }

    private List<String> resolveCandidateTags(String text, ProfileExtraction extraction) {
        Set<String> known = new LinkedHashSet<>();
        known.addAll(extraction.getActivityTypes());
        known.addAll(extraction.getAvailableTimes());
        known.addAll(extraction.getDistricts());
        known.addAll(extraction.getSkillLevels());
        if (StringUtils.isNotBlank(extraction.getCity())) {
            known.add(extraction.getCity());
        }

        Set<String> candidates = new LinkedHashSet<>();
        for (String token : TOKEN_SPLIT_PATTERN.split(text)) {
            String tag = token.trim();
            if (tag.length() < 2 || tag.length() > 16 || known.contains(tag)) {
                continue;
            }
            if (tag.matches(".*(电话|手机|邮箱|@|密码|token|Token).*")) {
                continue;
            }
            candidates.add(tag);
            if (candidates.size() >= 8) {
                break;
            }
        }
        return candidates.stream().toList();
    }

    private double resolveConfidence(ProfileExtraction extraction) {
        int signals = 0;
        signals += extraction.getActivityTypes().size();
        signals += extraction.getAvailableTimes().isEmpty() ? 0 : 1;
        signals += StringUtils.isBlank(extraction.getCity()) ? 0 : 1;
        signals += extraction.getSkillLevels().isEmpty() ? 0 : 1;
        signals += StringUtils.isBlank(extraction.getBudgetPreference()) ? 0 : 1;
        return Math.min(0.95, 0.35 + signals * 0.12);
    }
}

