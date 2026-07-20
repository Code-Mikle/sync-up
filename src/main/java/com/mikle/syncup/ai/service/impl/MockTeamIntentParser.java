package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.service.TeamIntentParser;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MockTeamIntentParser implements TeamIntentParser {

    private static final List<String> ACTIVITIES = Arrays.asList("羽毛球", "跳舞", "健身", "徒步", "跑步", "篮球", "电影", "桌游");

    private static final List<String> CITIES = Arrays.asList("西安", "北京", "上海", "广州", "深圳", "杭州", "成都", "重庆");

    private static final List<String> TEAM_KEYWORDS = Arrays.asList("搭子", "队伍", "组队", "活动", "找人", "约", "一起");

    private static final List<String> CREATE_KEYWORDS = Arrays.asList("创建", "发起", "新建", "组织");

    private static final Map<String, Integer> CHINESE_NUMBERS = new LinkedHashMap<>();

    private static final Pattern BUDGET_PATTERN = Pattern.compile("(?:预算|每人|人均)\\s*(\\d+(?:\\.\\d+)?)|((?:\\d+(?:\\.\\d+)?))\\s*元|((?:\\d+(?:\\.\\d+)?))\\s*(?:以内|以下|内)");

    private static final Pattern MEMBER_PATTERN = Pattern.compile("(?:找|约|缺|差)?\\s*([一二两三四五六七八九十两\\d]+)\\s*(?:个|人|位)(?:搭子|队友|人)?");

    static {
        CHINESE_NUMBERS.put("一", 1);
        CHINESE_NUMBERS.put("二", 2);
        CHINESE_NUMBERS.put("两", 2);
        CHINESE_NUMBERS.put("三", 3);
        CHINESE_NUMBERS.put("四", 4);
        CHINESE_NUMBERS.put("五", 5);
        CHINESE_NUMBERS.put("六", 6);
        CHINESE_NUMBERS.put("七", 7);
        CHINESE_NUMBERS.put("八", 8);
        CHINESE_NUMBERS.put("九", 9);
        CHINESE_NUMBERS.put("十", 10);
    }

    @Override
    public TeamIntent parse(String message) {
        TeamIntent intent = new TeamIntent();
        intent.setSourceText(message);
        if (StringUtils.isBlank(message)) {
            intent.getMissingFields().add("message");
            return intent;
        }
        String text = message.trim();
        parseActivity(text, intent);
        parseCity(text, intent);
        parseBudget(text, intent);
        parseMemberCount(text, intent);
        parseTime(text, intent);
        parseSkillLevel(text, intent);
        parseCreateRequest(text, intent);

        boolean teamRelated = hasAny(text, TEAM_KEYWORDS)
                || intent.getActivityType() != null
                || intent.getCity() != null
                || intent.isCreateTeamRequested();
        intent.setTeamRelated(teamRelated);
        if (intent.getActivityType() != null) {
            intent.getTags().add(intent.getActivityType());
        }
        if (teamRelated) {
            fillMissingFields(intent);
        }
        return intent;
    }

    private void parseActivity(String text, TeamIntent intent) {
        for (String activity : ACTIVITIES) {
            if (text.contains(activity)) {
                intent.setActivityType(activity);
                return;
            }
        }
    }

    private void parseCity(String text, TeamIntent intent) {
        for (String city : CITIES) {
            if (text.contains(city)) {
                intent.setCity(city);
                return;
            }
        }
    }

    private void parseBudget(String text, TeamIntent intent) {
        if (!containsAny(text, "预算", "每人", "人均", "元", "以内", "以下")) {
            return;
        }
        Matcher matcher = BUDGET_PATTERN.matcher(text);
        if (matcher.find()) {
            String budgetValue = firstNotBlank(matcher.group(1), matcher.group(2), matcher.group(3));
            if (budgetValue != null) {
                intent.setBudgetMax(new BigDecimal(budgetValue));
            }
        }
    }

    private void parseMemberCount(String text, TeamIntent intent) {
        Matcher matcher = MEMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            String before = text.substring(Math.max(0, matcher.start() - 2), matcher.start());
            if (before.contains("每")) {
                continue;
            }
            Integer number = parseNumber(matcher.group(1));
            if (number != null && number > 0) {
                intent.setMemberCount(number);
                return;
            }
        }
    }

    private void parseTime(String text, TeamIntent intent) {
        if (text.contains("今天")) {
            intent.setStartTime(hourAfterNow(2));
            return;
        }
        if (text.contains("明天")) {
            intent.setStartTime(dayAtHour(1, 19));
            return;
        }
        if (text.contains("下周")) {
            intent.setStartTime(nextDayOfWeek(Calendar.SATURDAY, 10, true));
            return;
        }
        if (text.contains("周末") || text.contains("星期六") || text.contains("周六")) {
            intent.setStartTime(nextDayOfWeek(Calendar.SATURDAY, 10, false));
            return;
        }
        if (text.contains("星期日") || text.contains("周日")) {
            intent.setStartTime(nextDayOfWeek(Calendar.SUNDAY, 10, false));
        }
    }

    private void parseSkillLevel(String text, TeamIntent intent) {
        if (containsAny(text, "入门", "新手")) {
            intent.setSkillLevel("入门");
            return;
        }
        if (containsAny(text, "中等", "中级")) {
            intent.setSkillLevel("中等");
            return;
        }
        if (containsAny(text, "熟练", "高手", "进阶")) {
            intent.setSkillLevel("熟练");
        }
    }

    private void parseCreateRequest(String text, TeamIntent intent) {
        intent.setCreateTeamRequested(hasAny(text, CREATE_KEYWORDS));
        if (intent.isCreateTeamRequested() && intent.getActivityType() != null) {
            String city = StringUtils.defaultIfBlank(intent.getCity(), "本地");
            intent.setTeamName(city + intent.getActivityType() + "搭子队");
            intent.setDescription("由 AI 根据用户需求生成的组队草稿，确认前不会写入业务表。");
        }
    }

    private void fillMissingFields(TeamIntent intent) {
        if (intent.getActivityType() == null) {
            intent.getMissingFields().add("activityType");
        }
        if (intent.getCity() == null) {
            intent.getMissingFields().add("city");
        }
        if (intent.isCreateTeamRequested() && intent.getMemberCount() == null) {
            intent.getMissingFields().add("memberCount");
        }
    }

    private Integer parseNumber(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        if (StringUtils.isNumeric(value)) {
            return Integer.parseInt(value);
        }
        return CHINESE_NUMBERS.get(value);
    }

    private Date hourAfterNow(int hours) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, hours);
        return calendar.getTime();
    }

    private Date dayAtHour(int offsetDays, int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, offsetDays);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private Date nextDayOfWeek(int targetDayOfWeek, int hour, boolean forceNextWeek) {
        Calendar calendar = Calendar.getInstance();
        int currentDay = calendar.get(Calendar.DAY_OF_WEEK);
        int daysToAdd = (targetDayOfWeek - currentDay + 7) % 7;
        if (daysToAdd == 0 || forceNextWeek) {
            daysToAdd += 7;
        }
        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private boolean hasAny(String text, List<String> values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
