package com.mikle.syncup.model.enums;

import lombok.Getter;
import cn.hutool.core.util.ObjUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum TeamActivityCategoryEnum {

    SPORT_FITNESS(1, "运动健身", "侧重室内运动、健身房、游泳、瑜伽、球类等"),
    OUTDOOR_TRAVEL(2, "户外出行", "侧重徒步、露营、骑行、飞盘、滑雪等本地或短途户外活动"),
    GAME_ESPORTS(3, "游戏电竞", "侧重手游、端游、主机游戏组队"),
    BOARD_GAME_SCRIPT(4, "桌游剧本", "侧重线下剧本杀、狼人杀、棋牌、桌游等"),
    LEISURE_ENTERTAINMENT(5, "休闲娱乐", "侧重看电影、K歌、看展、逛街、摄影等"),
    FOOD_EXPLORATION(6, "美食探店", "侧重吃饭、探店、咖啡、火锅、夜宵等"),
    LEARNING_GROWTH(7, "学习成长", "侧重考研、考证、语言交换、读书会、自习、刷题等"),
    TRAVEL_TOUR(8, "旅行出游", "侧重跨城市长途旅行、拼车、自驾游、结伴游等"),
    OTHER(9, "其他", "无法归入以上类别的活动");

    private final Integer code;

    private final String name;

    private final String description;

    TeamActivityCategoryEnum(Integer code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    /**
     * 获取所有code
     */
    public static List<Integer> getCodes() {
        return Arrays.stream(values())
                .map(TeamActivityCategoryEnum::getCode)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有name
     */
    public static List<String> getNames() {
        return Arrays.stream(values())
                .map(TeamActivityCategoryEnum::getName)
                .collect(Collectors.toList());
    }

    /**
     * 根据code获取枚举
     */
    public static TeamActivityCategoryEnum getEnumByCode(Integer code) {
        if (ObjUtil.isEmpty(code)) {
            return null;
        }
        for (TeamActivityCategoryEnum categoryEnum : TeamActivityCategoryEnum.values()) {
            if (categoryEnum.getCode().equals(code)) {
                return categoryEnum;
            }
        }
        return null;
    }

    /**
     * 根据name获取枚举
     */
    public static TeamActivityCategoryEnum getEnumByName(String name) {
        if (ObjUtil.isEmpty(name)) {
            return null;
        }
        for (TeamActivityCategoryEnum categoryEnum : TeamActivityCategoryEnum.values()) {
            if (categoryEnum.getName().equals(name)) {
                return categoryEnum;
            }
        }
        return null;
    }

    /**
     * 判断code是否合法
     */
    public static boolean isValidCode(Integer code) {
        return getEnumByCode(code) != null;
    }

    /**
     * 判断name是否存在
     */
    public static boolean isValidName(String name) {
        return getEnumByName(name) != null;
    }











}
