package com.mikle.syncup.ai.model.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Schema(description = "AI 识别出的队伍、搭子和个人资料相关意图")
public class TeamIntent implements Serializable {

    @Schema(description = "用户原始输入文本")
    private String sourceText;

    @Schema(description = "用户提到的队伍 id，用于查询队伍详情、加入或退出队伍等场景")
    private Long teamId;

    @Schema(description = "加密队伍密码，仅在用户提供队伍密码时使用")
    private String teamPassword;

    @Schema(description = "活动大类编码：1-运动健身，2-户外出行，3-游戏电竞，4-桌游剧本，5-休闲娱乐，6-美食探店，7-学习成长，8-旅行出游，9-其他")
    private Integer activityCategory;

    @Schema(description = "活动类型，例如足球、羽毛球、健身、徒步")
    private String activityType;

    @Schema(description = "城市名称，例如西安、北京、上海")
    private String city;

    @Schema(description = "区县、商圈、场馆或具体活动区域")
    private String district;

    @Schema(description = "活动开始时间")
    private Date startTime;

    @Schema(description = "活动持续时长，单位：分钟")
    private Integer durationMinutes;

    @Schema(description = "人数需求：创建队伍时表示队伍人数上限；搜索队伍时表示希望剩余可加入名额")
    private Integer memberCount;

    @Schema(description = "最低预算，通常表示每人最低费用")
    private BigDecimal budgetMin;

    @Schema(description = "最高预算，通常表示每人最高费用；免费或无需支付时可为 0")
    private BigDecimal budgetMax;

    @Schema(description = "技能水平或活动水平，例如入门、中等、熟练")
    private String skillLevel;

    @Schema(description = "用于匹配搭子或队伍的标签列表")
    private List<String> tags = new ArrayList<>();

    @Schema(description = "创建队伍草稿时建议的队伍名称")
    private String teamName;

    @Schema(description = "创建队伍草稿时建议的队伍描述")
    private String description;

    @Schema(description = "用户希望更新到个人资料中的自我介绍或画像文本")
    private String profileText;

    @Schema(description = "是否请求创建队伍；为 true 时只能生成草稿，不能直接创建正式队伍")
    private boolean createTeamRequested;

    @Schema(description = "是否属于队伍、搭子或个人资料相关需求")
    private boolean teamRelated;

    @Schema(description = "当前意图缺失的必要字段，例如 activityCategory、city、memberCount")
    private List<String> missingFields = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
