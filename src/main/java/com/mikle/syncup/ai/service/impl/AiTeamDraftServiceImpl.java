package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mikle.syncup.ai.mapper.AiTeamDraftMapper;
import com.mikle.syncup.ai.model.AiTeamDraft;
import com.mikle.syncup.ai.model.AiTeamDraftConfirmResponse;
import com.mikle.syncup.ai.model.TeamDraft;
import com.mikle.syncup.ai.service.AiTeamDraftService;
import com.mikle.syncup.ai.service.AiToolCallLogService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.service.TeamService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class AiTeamDraftServiceImpl extends ServiceImpl<AiTeamDraftMapper, AiTeamDraft> implements AiTeamDraftService {

    private static final int STATUS_PENDING = 0;

    private static final int STATUS_CONFIRMED = 1;

    private static final int STATUS_EXPIRED = 2;

    @Resource
    private AiTeamDraftMapper aiTeamDraftMapper;

    @Resource
    private TeamService teamService;

    @Resource
    private AiToolCallLogService aiToolCallLogService;

    @Override
    public TeamDraft saveDraft(TeamDraft draft, User loginUser, String sessionId) {
        if (draft == null || loginUser == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        AiTeamDraft entity = new AiTeamDraft();
        BeanUtils.copyProperties(draft, entity);
        entity.setSessionId(sessionId);
        entity.setUserId(loginUser.getId());
        entity.setStatus(STATUS_PENDING);
        boolean saved = this.save(entity);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "save AI team draft failed");
        }
        return toTeamDraft(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTeamDraftConfirmResponse confirmDraft(String draftId, User loginUser) {
        long start = System.currentTimeMillis();
        String normalizedDraftId = StringUtils.isBlank(draftId) ? draftId : draftId.trim();
        AiTeamDraft draft = null;
        try {
            AiTeamDraftConfirmResponse response = doConfirmDraft(normalizedDraftId, loginUser);
            AiTeamDraft confirmedDraft = findDraftByDraftId(response.getDraftId());
            aiToolCallLogService.recordDraftConfirm(
                    confirmedDraft == null ? null : confirmedDraft.getSessionId(),
                    loginUser,
                    response.getDraftId(),
                    response.getTeamId(),
                    "success",
                    "teamId=" + response.getTeamId(),
                    null,
                    System.currentTimeMillis() - start
            );
            return response;
        } catch (RuntimeException e) {
            if (StringUtils.isNotBlank(normalizedDraftId)) {
                draft = findDraftByDraftId(normalizedDraftId);
            }
            aiToolCallLogService.recordDraftConfirm(
                    draft == null ? null : draft.getSessionId(),
                    loginUser,
                    normalizedDraftId,
                    null,
                    "failed",
                    null,
                    getErrorMessage(e),
                    System.currentTimeMillis() - start
            );
            throw e;
        }
    }

    private AiTeamDraftConfirmResponse doConfirmDraft(String draftId, User loginUser) {
        if (StringUtils.isBlank(draftId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "draftId is required");
        }
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        AiTeamDraft draft = aiTeamDraftMapper.lockByDraftId(draftId);
        if (draft == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "draft does not exist");
        }
        if (!draft.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "no permission to confirm this draft");
        }
        if (STATUS_CONFIRMED == safeStatus(draft)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "draft has already been confirmed");
        }
        Date now = new Date();
        if (draft.getExpiresAt() != null && draft.getExpiresAt().before(now)) {
            AiTeamDraft updateDraft = new AiTeamDraft();
            updateDraft.setId(draft.getId());
            updateDraft.setStatus(STATUS_EXPIRED);
            this.updateById(updateDraft);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "draft has expired");
        }

        Team team = new Team();
        team.setName(draft.getName());
        team.setDescription(draft.getDescription());
        team.setMaxNum(draft.getMaxNum());
        team.setStatus(0);
        team.setExpireTime(resolveTeamExpireTime(draft, now));
        team.setActivityType(draft.getActivityType());
        team.setCity(draft.getCity());
        team.setDistrict(draft.getDistrict());
        team.setStartTime(draft.getStartTime());
        team.setDurationMinutes(draft.getDurationMinutes());
        team.setBudgetPerPerson(draft.getBudgetPerPerson());
        team.setSkillLevel(draft.getSkillLevel());
        Long teamId = teamService.addTeam(team, loginUser);

        AiTeamDraft updateDraft = new AiTeamDraft();
        updateDraft.setId(draft.getId());
        updateDraft.setStatus(STATUS_CONFIRMED);
        updateDraft.setConfirmedTeamId(teamId);
        updateDraft.setConfirmedAt(now);
        boolean updated = this.updateById(updateDraft);
        if (!updated) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "confirm AI team draft failed");
        }

        AiTeamDraftConfirmResponse response = new AiTeamDraftConfirmResponse();
        response.setDraftId(draft.getDraftId());
        response.setTeamId(teamId);
        response.setStatus("confirmed");
        return response;
    }

    private String getErrorMessage(RuntimeException e) {
        if (e instanceof BusinessException businessException) {
            return StringUtils.defaultIfBlank(businessException.getDescription(), businessException.getMessage());
        }
        return e.getMessage();
    }

    private AiTeamDraft findDraftByDraftId(String draftId) {
        return getOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiTeamDraft>()
                .eq("draftId", draftId)
                .last("limit 1"));
    }

    private int safeStatus(AiTeamDraft draft) {
        return draft.getStatus() == null ? STATUS_PENDING : draft.getStatus();
    }

    private Date resolveTeamExpireTime(AiTeamDraft draft, Date now) {
        if (draft.getStartTime() != null && draft.getStartTime().after(now)) {
            return draft.getStartTime();
        }
        return new Date(now.getTime() + 7L * 24 * 60 * 60 * 1000);
    }

    private TeamDraft toTeamDraft(AiTeamDraft entity) {
        TeamDraft draft = new TeamDraft();
        BeanUtils.copyProperties(entity, draft);
        return draft;
    }
}
