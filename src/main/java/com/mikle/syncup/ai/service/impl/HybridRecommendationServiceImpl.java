package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileEmbeddingMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.entity.AiTeamEmbedding;
import com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.model.vo.AiUserRecommendation;
import com.mikle.syncup.ai.model.vo.HybridRecommendationResult;
import com.mikle.syncup.ai.service.HybridRecommendationService;
import com.mikle.syncup.ai.service.AiTeamEmbeddingService;
import com.mikle.syncup.ai.service.ProfileEmbeddingCodec;
import com.mikle.syncup.ai.service.ProfileEmbeddingGenerator;
import com.mikle.syncup.ai.service.RecommendationQueryTextBuilder;
import com.mikle.syncup.ai.service.TeamRetrievalTextBuilder;
import com.mikle.syncup.ai.service.TextHashService;
import com.mikle.syncup.ai.service.VectorSimilarity;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.model.dto.TeamQuery;
import com.mikle.syncup.model.vo.TeamUserVO;
import com.mikle.syncup.service.TeamService;
import com.mikle.syncup.service.UserTeamService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HybridRecommendationServiceImpl implements HybridRecommendationService {

    private static final int ACTIVE = 1;
    private static final int CANDIDATE_LIMIT = 100;

    @Resource
    private UserMapper userMapper;

    @Resource
    private AiUserProfileMapper profileMapper;

    @Resource
    private AiUserProfileEmbeddingMapper userEmbeddingMapper;

    @Resource
    private AiTeamEmbeddingService teamEmbeddingService;

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private ProfileEmbeddingGenerator embeddingGenerator;

    @Resource
    private ProfileEmbeddingCodec embeddingCodec;

    @Resource
    private VectorSimilarity vectorSimilarity;

    @Resource
    private RecommendationQueryTextBuilder queryTextBuilder;

    @Resource
    private TeamRetrievalTextBuilder teamTextBuilder;

    @Resource
    private TextHashService textHashService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public HybridRecommendationResult<AiUserRecommendation> recommendUsers(
            TeamIntent intent, User loginUser, int limit) {
        validate(loginUser, limit);
        long start = System.currentTimeMillis();
        User currentUser = userMapper.selectById(loginUser.getId());
        if (currentUser == null) {
            return emptyResult(start);
        }
        AiUserProfileEntity currentProfile = getActiveProfile(currentUser.getId());
        Set<String> requestedTags = requestedTags(intent, currentUser);
        String effectiveCity = firstNonBlank(intent == null ? null : intent.getCity(), currentUser.getCity());
        String queryText = queryTextBuilder.build(intent, currentProfile);
        if (StringUtils.isBlank(queryText) && requestedTags.isEmpty() && StringUtils.isBlank(effectiveCity)) {
            return emptyResult(start);
        }

        List<User> candidates = loadUserCandidates(currentUser.getId(), effectiveCity, requestedTags).stream()
                .filter(candidate -> requestedTags.isEmpty()
                        || !Collections.disjoint(requestedTags, parseTags(candidate.getTags())))
                .limit(CANDIDATE_LIMIT)
                .toList();
        if (candidates.isEmpty()) {
            return emptyResult(start);
        }

        QueryVector queryVector = createQueryVector(queryText);
        Map<Long, AiUserProfileEntity> candidateProfiles = loadProfiles(
                candidates.stream().map(User::getId).toList());
        Map<Long, AiUserProfileEmbedding> embeddings = loadUserEmbeddings(
                candidates.stream().map(User::getId).toList());

        List<ScoredUser> scored = new ArrayList<>();
        boolean usedSemantic = false;
        for (User candidate : candidates) {
            Set<String> candidateTags = parseTags(candidate.getTags());
            double tagScore = overlapScore(requestedTags, candidateTags);
            double recencyScore = recencyScore(candidate.getLastActiveTime());
            Double semanticScore = semanticUserScore(queryVector, candidate.getId(), candidateProfiles, embeddings);
            if (semanticScore != null) {
                usedSemantic = true;
            }
            double totalScore = semanticScore == null
                    ? 0.75D * tagScore + 0.25D * recencyScore
                    : 0.75D * semanticScore + 0.15D * tagScore + 0.10D * recencyScore;
            scored.add(new ScoredUser(candidate, totalScore, semanticScore, tagScore, recencyScore));
        }
        boolean degraded = !usedSemantic || scored.stream().anyMatch(item -> item.semanticScore() == null);
        List<AiUserRecommendation> items = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredUser::totalScore).reversed()
                        .thenComparing(scoredUser -> scoredUser.user().getId()))
                .limit(limit)
                .map(scoredUser -> toRecommendation(
                        scoredUser, effectiveCity, requestedTags, degraded))
                .toList();
        long duration = System.currentTimeMillis() - start;
        log.info("hybrid user recommendation completed, userId={}, candidates={}, results={}, degraded={}, durationMs={}",
                currentUser.getId(), candidates.size(), items.size(), degraded, duration);
        return new HybridRecommendationResult<>(items, candidates.size(), degraded,
                queryVector == null ? null : queryVector.model(), duration);
    }

    @Override
    public HybridRecommendationResult<TeamUserVO> recommendTeams(
            TeamIntent intent, User loginUser, int limit) {
        validate(loginUser, limit);
        long start = System.currentTimeMillis();
        User currentUser = userMapper.selectById(loginUser.getId());
        if (currentUser == null) {
            return emptyResult(start);
        }
        TeamQuery teamQuery = buildTeamQuery(intent, currentUser);
        int requiredSlots = intent == null || intent.getMemberCount() == null
                ? 1 : Math.max(1, intent.getMemberCount());
        List<TeamUserVO> candidates = teamService.listTeams(teamQuery, false).stream()
                .filter(team -> fillAndCheckAvailableSlots(team, requiredSlots))
                .limit(CANDIDATE_LIMIT)
                .toList();
        if (candidates.isEmpty()) {
            return emptyResult(start);
        }

        AiUserProfileEntity currentProfile = getActiveProfile(currentUser.getId());
        QueryVector queryVector = createQueryVector(queryTextBuilder.build(intent, currentProfile));
        Map<Long, AiTeamEmbedding> embeddings = teamEmbeddingService.getActiveEmbeddings(
                candidates.stream().map(TeamUserVO::getId).toList());
        List<ScoredTeam> scored = new ArrayList<>();
        boolean usedSemantic = false;
        for (TeamUserVO candidate : candidates) {
            double businessScore = teamBusinessScore(intent, candidate);
            Double semanticScore = semanticTeamScore(queryVector, candidate, embeddings.get(candidate.getId()));
            if (semanticScore != null) {
                usedSemantic = true;
            }
            double totalScore = semanticScore == null
                    ? businessScore
                    : 0.80D * semanticScore + 0.20D * businessScore;
            scored.add(new ScoredTeam(candidate, totalScore, semanticScore, businessScore));
        }
        boolean degraded = !usedSemantic || scored.stream().anyMatch(item -> item.semanticScore() == null);
        List<TeamUserVO> items = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredTeam::totalScore).reversed()
                        .thenComparing(scoredTeam -> scoredTeam.team().getId()))
                .limit(limit)
                .map(scoredTeam -> addTeamReasons(scoredTeam, intent, degraded))
                .toList();
        long duration = System.currentTimeMillis() - start;
        log.info("hybrid team recommendation completed, userId={}, candidates={}, results={}, degraded={}, durationMs={}",
                currentUser.getId(), candidates.size(), items.size(), degraded, duration);
        return new HybridRecommendationResult<>(items, candidates.size(), degraded,
                queryVector == null ? null : queryVector.model(), duration);
    }

    private List<User> loadUserCandidates(long currentUserId, String city, Set<String> requestedTags) {
        QueryWrapper<User> query = new QueryWrapper<User>()
                .eq("userStatus", 0)
                .ne("id", currentUserId);
        if (StringUtils.isNotBlank(city)) {
            query.eq("city", city.trim());
        }
        if (!requestedTags.isEmpty()) {
            query.and(tagsQuery -> {
                Iterator<String> iterator = requestedTags.iterator();
                tagsQuery.apply(tagMatchSql(), iterator.next());
                while (iterator.hasNext()) {
                    tagsQuery.or().apply(tagMatchSql(), iterator.next());
                }
            });
        }
        query.orderByDesc("lastActiveTime")
                .orderByDesc("id")
                .last("limit " + CANDIDATE_LIMIT);
        return userMapper.selectList(query);
    }

    private String tagMatchSql() {
        return "CASE WHEN JSON_VALID(tags) "
                + "THEN JSON_CONTAINS(tags, JSON_QUOTE({0})) "
                + "ELSE FIND_IN_SET({0}, REPLACE(tags, '，', ',')) > 0 END";
    }

    private Map<Long, AiUserProfileEntity> loadProfiles(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return profileMapper.selectList(new QueryWrapper<AiUserProfileEntity>()
                        .in("userId", userIds)
                        .eq("status", ACTIVE))
                .stream()
                .collect(Collectors.toMap(AiUserProfileEntity::getUserId, profile -> profile, (a, b) -> a));
    }

    private Map<Long, AiUserProfileEmbedding> loadUserEmbeddings(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, AiUserProfileEmbedding> result = new LinkedHashMap<>();
        userEmbeddingMapper.selectList(new QueryWrapper<AiUserProfileEmbedding>()
                        .in("userId", userIds)
                        .eq("status", ACTIVE)
                        .orderByDesc("profileVersion"))
                .forEach(embedding -> result.putIfAbsent(embedding.getUserId(), embedding));
        return result;
    }

    private Double semanticUserScore(QueryVector queryVector,
                                     long userId,
                                     Map<Long, AiUserProfileEntity> profiles,
                                     Map<Long, AiUserProfileEmbedding> embeddings) {
        if (queryVector == null) {
            return null;
        }
        AiUserProfileEntity profile = profiles.get(userId);
        AiUserProfileEmbedding embedding = embeddings.get(userId);
        if (profile == null || embedding == null
                || !Objects.equals(profile.getProfileVersion(), embedding.getProfileVersion())
                || !Objects.equals(queryVector.model(), embedding.getEmbeddingModel())
                || !Objects.equals(queryVector.vector().length, embedding.getDimensions())) {
            return null;
        }
        try {
            return normalizeCosine(vectorSimilarity.cosine(
                    queryVector.vector(), embeddingCodec.deserialize(embedding.getVectorJson())));
        } catch (RuntimeException e) {
            log.warn("skip invalid user embedding, userId={}", userId, e);
            return null;
        }
    }

    private Double semanticTeamScore(QueryVector queryVector, TeamUserVO team, AiTeamEmbedding embedding) {
        if (queryVector == null || embedding == null
                || !Objects.equals(queryVector.model(), embedding.getEmbeddingModel())
                || !Objects.equals(queryVector.vector().length, embedding.getDimensions())) {
            return null;
        }
        com.mikle.syncup.model.domain.Team teamEntity = new com.mikle.syncup.model.domain.Team();
        org.springframework.beans.BeanUtils.copyProperties(team, teamEntity);
        String currentHash = textHashService.sha256(teamTextBuilder.build(teamEntity));
        if (!Objects.equals(currentHash, embedding.getContentHash())) {
            return null;
        }
        try {
            return normalizeCosine(vectorSimilarity.cosine(
                    queryVector.vector(), embeddingCodec.deserialize(embedding.getVectorJson())));
        } catch (RuntimeException e) {
            log.warn("skip invalid team embedding, teamId={}", team.getId(), e);
            return null;
        }
    }

    private QueryVector createQueryVector(String queryText) {
        if (StringUtils.isBlank(queryText) || !embeddingGenerator.isAvailable()) {
            return null;
        }
        try {
            GeneratedEmbedding generated = embeddingGenerator.generate(queryText);
            return new QueryVector(generated.model(), embeddingCodec.normalize(generated.vector()));
        } catch (RuntimeException e) {
            log.warn("create recommendation query embedding failed, fallback to structured ranking, errorType={}, message={}",
                    e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private TeamQuery buildTeamQuery(TeamIntent intent, User currentUser) {
        TeamQuery query = new TeamQuery();
        if (intent != null) {
            query.setActivityCategory(intent.getActivityCategory());
            query.setActivityType(intent.getActivityType());
            query.setCity(firstNonBlank(intent.getCity(), currentUser.getCity()));
            query.setDistrict(intent.getDistrict());
            query.setMaxBudgetPerPerson(intent.getBudgetMax());
            query.setSkillLevel(intent.getSkillLevel());
            if (intent.getStartTime() != null) {
                long window = TimeUnit.HOURS.toMillis(3);
                query.setStartTimeBegin(new Date(intent.getStartTime().getTime() - window));
                query.setStartTimeEnd(new Date(intent.getStartTime().getTime() + window));
            }
        } else {
            query.setCity(currentUser.getCity());
        }
        query.setOnlyAvailable(true);
        query.setStatus(0);
        return query;
    }

    private boolean fillAndCheckAvailableSlots(TeamUserVO team, int requiredSlots) {
        long joined = userTeamService.count(new QueryWrapper<UserTeam>().eq("teamId", team.getId()));
        team.setHasJoinNum(Math.toIntExact(joined));
        return team.getMaxNum() != null && team.getMaxNum() - joined >= requiredSlots;
    }

    private double teamBusinessScore(TeamIntent intent, TeamUserVO team) {
        double score = 0.4D;
        if (intent != null && StringUtils.isNotBlank(intent.getDistrict())
                && intent.getDistrict().trim().equalsIgnoreCase(StringUtils.defaultString(team.getDistrict()))) {
            score += 0.20D;
        }
        if (intent != null && intent.getBudgetMax() != null && team.getBudgetPerPerson() != null) {
            BigDecimal max = intent.getBudgetMax();
            if (team.getBudgetPerPerson().compareTo(max) <= 0) {
                score += 0.15D;
            }
        }
        if (intent != null && intent.getStartTime() != null && team.getStartTime() != null) {
            long distance = Math.abs(intent.getStartTime().getTime() - team.getStartTime().getTime());
            score += distance <= TimeUnit.HOURS.toMillis(1) ? 0.15D : 0.05D;
        }
        int available = team.getMaxNum() == null || team.getHasJoinNum() == null
                ? 0 : team.getMaxNum() - team.getHasJoinNum();
        if (available > 0) {
            score += 0.10D;
        }
        return Math.min(1D, score);
    }

    private AiUserRecommendation toRecommendation(ScoredUser scored,
                                                  String effectiveCity,
                                                  Set<String> requestedTags,
                                                  boolean degraded) {
        User user = scored.user();
        AiUserRecommendation result = new AiUserRecommendation();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setAvatarUrl(user.getAvatarUrl());
        result.setGender(user.getGender());
        result.setCity(user.getCity());
        result.setTags(user.getTags());
        result.setCreateTime(user.getCreateTime());
        result.setLastActiveTime(user.getLastActiveTime());
        result.setDegraded(degraded);
        if (StringUtils.isNotBlank(effectiveCity) && effectiveCity.equals(user.getCity())) {
            result.getReasons().add("所在城市符合本次要求");
        }
        Set<String> commonTags = new LinkedHashSet<>(requestedTags);
        commonTags.retainAll(parseTags(user.getTags()));
        if (!commonTags.isEmpty()) {
            result.getReasons().add("共同偏好：" + String.join("、", commonTags.stream().limit(2).toList()));
        }
        if (scored.semanticScore() != null) {
            result.getReasons().add("活动与社交偏好较接近");
        }
        if (scored.recencyScore() >= 1D) {
            result.getReasons().add("近期较活跃");
        }
        if (result.getReasons().isEmpty()) {
            result.getReasons().add("符合本次筛选条件");
        }
        return result;
    }

    private TeamUserVO addTeamReasons(ScoredTeam scored, TeamIntent intent, boolean degraded) {
        TeamUserVO team = scored.team();
        team.setRecommendationDegraded(degraded);
        if (intent != null && StringUtils.isNotBlank(intent.getActivityType())) {
            team.getRecommendationReasons().add("活动类型符合本次要求");
        }
        if (intent != null && StringUtils.isNotBlank(intent.getCity())) {
            team.getRecommendationReasons().add("活动城市符合本次要求");
        }
        if (scored.semanticScore() != null) {
            team.getRecommendationReasons().add("活动描述与个人偏好较接近");
        }
        if (team.getMaxNum() != null && team.getHasJoinNum() != null) {
            team.getRecommendationReasons().add("当前剩余 "
                    + Math.max(0, team.getMaxNum() - team.getHasJoinNum()) + " 个名额");
        }
        if (team.getRecommendationReasons().isEmpty()) {
            team.getRecommendationReasons().add("符合本次筛选条件");
        }
        return team;
    }

    private Set<String> requestedTags(TeamIntent intent, User currentUser) {
        Set<String> tags = new LinkedHashSet<>();
        if (intent != null) {
            addTag(tags, intent.getActivityType());
            if (intent.getTags() != null) {
                intent.getTags().forEach(tag -> addTag(tags, tag));
            }
        }
        if (tags.isEmpty()) {
            tags.addAll(parseTags(currentUser.getTags()));
        }
        return tags;
    }

    private Set<String> parseTags(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptySet();
        }
        try {
            List<String> values = objectMapper.readValue(value, new TypeReference<>() {
            });
            return normalizeTags(values);
        } catch (Exception ignored) {
            return normalizeTags(List.of(value.split("[,，]")));
        }
    }

    private Set<String> normalizeTags(Collection<String> tags) {
        Set<String> result = new LinkedHashSet<>();
        if (tags != null) {
            tags.forEach(tag -> addTag(result, tag));
        }
        return result;
    }

    private void addTag(Set<String> tags, String tag) {
        if (StringUtils.isNotBlank(tag)) {
            tags.add(tag.trim().toLowerCase(Locale.ROOT));
        }
    }

    private double overlapScore(Set<String> requested, Set<String> candidate) {
        if (requested.isEmpty() || candidate.isEmpty()) {
            return 0D;
        }
        Set<String> intersection = new LinkedHashSet<>(requested);
        intersection.retainAll(candidate);
        Set<String> union = new LinkedHashSet<>(requested);
        union.addAll(candidate);
        return union.isEmpty() ? 0D : (double) intersection.size() / union.size();
    }

    private double recencyScore(Date lastActiveTime) {
        if (lastActiveTime == null) {
            return 0D;
        }
        long age = Math.max(0L, System.currentTimeMillis() - lastActiveTime.getTime());
        if (age <= TimeUnit.DAYS.toMillis(30)) {
            return 1D;
        }
        return age <= TimeUnit.DAYS.toMillis(90) ? 0.5D : 0D;
    }

    private double normalizeCosine(double cosine) {
        return Math.max(0D, Math.min(1D, (cosine + 1D) / 2D));
    }

    private AiUserProfileEntity getActiveProfile(long userId) {
        return profileMapper.selectOne(new QueryWrapper<AiUserProfileEntity>()
                .eq("userId", userId)
                .eq("status", ACTIVE)
                .last("limit 1"));
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first.trim()
                : StringUtils.isNotBlank(second) ? second.trim() : null;
    }

    private void validate(User loginUser, int limit) {
        if (loginUser == null || loginUser.getId() <= 0) {
            throw new IllegalArgumentException("login user is required");
        }
        if (limit <= 0 || limit > 20) {
            throw new IllegalArgumentException("recommendation limit is invalid");
        }
    }

    private <T> HybridRecommendationResult<T> emptyResult(long start) {
        return new HybridRecommendationResult<>(Collections.emptyList(), 0, true,
                null, System.currentTimeMillis() - start);
    }

    private record QueryVector(String model, float[] vector) {
    }

    private record ScoredUser(User user, double totalScore, Double semanticScore,
                              double tagScore, double recencyScore) {
    }

    private record ScoredTeam(TeamUserVO team, double totalScore, Double semanticScore,
                              double businessScore) {
    }
}
