package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mikle.syncup.ai.mapper.AiTeamEmbeddingMapper;
import com.mikle.syncup.ai.model.entity.AiTeamEmbedding;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.service.AiTeamEmbeddingService;
import com.mikle.syncup.ai.service.ProfileEmbeddingCodec;
import com.mikle.syncup.ai.service.ProfileEmbeddingGenerator;
import com.mikle.syncup.ai.service.TeamRetrievalTextBuilder;
import com.mikle.syncup.ai.service.TextHashService;
import com.mikle.syncup.mapper.TeamMapper;
import com.mikle.syncup.model.domain.Team;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class AiTeamEmbeddingServiceImpl implements AiTeamEmbeddingService {

    private static final int ACTIVE = 1;
    private static final int BATCH_SIZE = 20;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private AiTeamEmbeddingMapper embeddingMapper;

    @Resource
    private ProfileEmbeddingGenerator embeddingGenerator;

    @Resource
    private ProfileEmbeddingCodec embeddingCodec;

    @Resource
    private TeamRetrievalTextBuilder textBuilder;

    @Resource
    private TextHashService textHashService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public int refreshPendingTeams() {
        if (!embeddingGenerator.isAvailable()) {
            return 0;
        }
        List<Team> teams = teamMapper.selectList(new QueryWrapper<Team>()
                .eq("status", 0)
                .and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"))
                .orderByAsc("id"));
        int refreshed = 0;
        for (Team team : teams) {
            if (refreshed >= BATCH_SIZE || isCurrent(team)) {
                continue;
            }
            try {
                refreshTeam(team);
                refreshed++;
            } catch (Exception e) {
                log.warn("refresh AI team embedding failed, teamId={}", team.getId(), e);
            }
        }
        return refreshed;
    }

    @Override
    public void invalidate(long teamId) {
        if (teamId <= 0) {
            return;
        }
        embeddingMapper.update(null, new UpdateWrapper<AiTeamEmbedding>()
                .set("status", 0)
                .eq("teamId", teamId)
                .eq("status", ACTIVE));
    }

    @Override
    public void deletePhysically(long teamId) {
        if (teamId > 0) {
            embeddingMapper.deletePhysicallyByTeamId(teamId);
        }
    }

    @Override
    public Map<Long, AiTeamEmbedding> getActiveEmbeddings(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AiTeamEmbedding> embeddings = embeddingMapper.selectList(
                new QueryWrapper<AiTeamEmbedding>()
                        .in("teamId", teamIds)
                        .eq("status", ACTIVE)
                        .orderByDesc("contentVersion"));
        Map<Long, AiTeamEmbedding> result = new LinkedHashMap<>();
        for (AiTeamEmbedding embedding : embeddings) {
            result.putIfAbsent(embedding.getTeamId(), embedding);
        }
        return result;
    }

    private boolean isCurrent(Team team) {
        AiTeamEmbedding active = getActive(team.getId());
        if (active == null || !Objects.equals(active.getEmbeddingModel(), embeddingGenerator.modelName())) {
            return false;
        }
        return Objects.equals(active.getContentHash(), textHashService.sha256(textBuilder.build(team)));
    }

    private void refreshTeam(Team team) {
        String retrievalText = textBuilder.build(team);
        String contentHash = textHashService.sha256(retrievalText);
        GeneratedEmbedding generated = embeddingGenerator.generate(retrievalText);
        float[] normalized = embeddingCodec.normalize(generated.vector());
        transactionTemplate.executeWithoutResult(status -> {
            Team latest = teamMapper.selectById(team.getId());
            if (latest == null || latest.getStatus() == null || latest.getStatus() != 0) {
                return;
            }
            String latestHash = textHashService.sha256(textBuilder.build(latest));
            if (!contentHash.equals(latestHash)) {
                return;
            }
            AiTeamEmbedding active = getActive(team.getId());
            if (active != null
                    && contentHash.equals(active.getContentHash())
                    && Objects.equals(generated.model(), active.getEmbeddingModel())) {
                return;
            }
            AiTeamEmbedding latestVersion = embeddingMapper.selectOne(
                    new QueryWrapper<AiTeamEmbedding>()
                            .eq("teamId", team.getId())
                            .orderByDesc("contentVersion")
                            .last("limit 1"));
            int contentVersion = latestVersion == null || latestVersion.getContentVersion() == null
                    ? 1 : latestVersion.getContentVersion() + 1;
            invalidate(team.getId());
            AiTeamEmbedding embedding = new AiTeamEmbedding();
            embedding.setTeamId(team.getId());
            embedding.setContentVersion(contentVersion);
            embedding.setContentHash(contentHash);
            embedding.setEmbeddingModel(generated.model());
            embedding.setDimensions(normalized.length);
            embedding.setVectorJson(embeddingCodec.serialize(normalized));
            embedding.setStatus(ACTIVE);
            embedding.setGeneratedAt(new Date());
            if (embeddingMapper.insert(embedding) <= 0) {
                throw new IllegalStateException("save AI team embedding failed");
            }
        });
    }

    private AiTeamEmbedding getActive(long teamId) {
        return embeddingMapper.selectOne(new QueryWrapper<AiTeamEmbedding>()
                .eq("teamId", teamId)
                .eq("status", ACTIVE)
                .orderByDesc("contentVersion")
                .last("limit 1"));
    }
}
