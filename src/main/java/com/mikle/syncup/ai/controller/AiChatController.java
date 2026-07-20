package com.mikle.syncup.ai.controller;

import com.mikle.syncup.ai.model.dto.AiChatRequest;
import com.mikle.syncup.ai.model.vo.AiChatHistoryVO;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.model.dto.AiProfileConfirmRequest;
import com.mikle.syncup.ai.model.dto.AiProfileExtractRequest;
import com.mikle.syncup.ai.model.vo.AiProfileResponse;
import com.mikle.syncup.ai.model.dto.AiTeamDetailsRequest;
import com.mikle.syncup.ai.model.vo.AiTeamDraftConfirmResponse;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.service.AiChatService;
import com.mikle.syncup.ai.service.AiTeamDraftService;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.common.BaseResponse;
import com.mikle.syncup.common.ResultUtils;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@CrossOrigin(origins = {"http://localhost:3000"})
public class AiChatController {

    @Resource
    private AiChatService aiChatService;

    @Resource
    private AiTeamDraftService aiTeamDraftService;

    @Resource
    private AiUserProfileService aiUserProfileService;

    @Resource
    private UserService userService;

    @PostMapping("/chat")
    public BaseResponse<AiChatResponseVO> chat(@RequestBody AiChatRequest aiChatRequest, HttpServletRequest request) {
        return ResultUtils.success(aiChatService.chat(aiChatRequest, request));
    }

    @GetMapping("/chat/history")
    public BaseResponse<AiChatHistoryVO> getChatHistory(HttpServletRequest request) {
        return ResultUtils.success(aiChatService.getHistory(request));
    }

    @PostMapping("/team-draft/{draftId}/confirm")
    public BaseResponse<AiTeamDraftConfirmResponse> confirmTeamDraft(@PathVariable String draftId,
                                                                     HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(aiTeamDraftService.confirmDraft(draftId, loginUser));
    }

    @PostMapping("/team/{teamId}/details")
    public BaseResponse<AiToolResult> getTeamDetails(@PathVariable Long teamId,
                                                     @RequestBody(required = false) AiTeamDetailsRequest aiTeamDetailsRequest,
                                                     HttpServletRequest request) {
        return ResultUtils.success(aiChatService.getTeamDetails(teamId, aiTeamDetailsRequest, request));
    }

    @PostMapping("/team/{teamId}/delete/confirm")
    public BaseResponse<AiToolResult> confirmDeleteTeam(@PathVariable Long teamId,
                                                        @RequestBody(required = false) AiTeamDetailsRequest aiTeamDetailsRequest,
                                                        HttpServletRequest request) {
        return ResultUtils.success(aiChatService.deleteTeam(teamId, aiTeamDetailsRequest, request));
    }

    @GetMapping("/profile/current")
    public BaseResponse<AiProfileResponse> getCurrentProfile(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(aiUserProfileService.getCurrentProfile(loginUser));
    }

    @PostMapping("/profile-draft")
    public BaseResponse<AiProfileResponse> createProfileDraft(@RequestBody AiProfileExtractRequest extractRequest,
                                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String sourceText = extractRequest == null ? null : extractRequest.getSourceText();
        return ResultUtils.success(aiUserProfileService.createProfileDraft(sourceText, loginUser));
    }

    @PostMapping("/profile-draft/{draftId}/confirm")
    public BaseResponse<AiProfileResponse> confirmProfileDraft(@PathVariable String draftId,
                                                               @RequestBody(required = false) AiProfileConfirmRequest confirmRequest,
                                                               HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(aiUserProfileService.confirmDraft(draftId, confirmRequest, loginUser));
    }

    @PostMapping("/profile-draft/{draftId}/reject")
    public BaseResponse<AiProfileResponse> rejectProfileDraft(@PathVariable String draftId,
                                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(aiUserProfileService.rejectDraft(draftId, loginUser));
    }
}
