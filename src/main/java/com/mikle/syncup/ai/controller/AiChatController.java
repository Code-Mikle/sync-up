package com.mikle.syncup.ai.controller;

import com.mikle.syncup.ai.model.AiChatRequest;
import com.mikle.syncup.ai.model.AiChatResponse;
import com.mikle.syncup.ai.model.AiProfileConfirmRequest;
import com.mikle.syncup.ai.model.AiProfileExtractRequest;
import com.mikle.syncup.ai.model.AiProfileResponse;
import com.mikle.syncup.ai.model.AiTeamDetailsRequest;
import com.mikle.syncup.ai.model.AiTeamDraftConfirmResponse;
import com.mikle.syncup.ai.model.AiToolResult;
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
    public BaseResponse<AiChatResponse> chat(@RequestBody AiChatRequest aiChatRequest, HttpServletRequest request) {
        return ResultUtils.success(aiChatService.chat(aiChatRequest, request));
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

    @GetMapping("/profile/current")
    public BaseResponse<AiProfileResponse> getCurrentProfile(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(aiUserProfileService.getCurrentProfile(loginUser));
    }

    @PostMapping("/profile/extract")
    public BaseResponse<AiProfileResponse> extractProfile(@RequestBody AiProfileExtractRequest extractRequest,
                                                          HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String sourceText = extractRequest == null ? null : extractRequest.getSourceText();
        return ResultUtils.success(aiUserProfileService.extractProfile(sourceText, loginUser));
    }

    @PostMapping("/profile-task/{taskId}/confirm")
    public BaseResponse<AiProfileResponse> confirmProfileTask(@PathVariable String taskId,
                                                              @RequestBody(required = false) AiProfileConfirmRequest confirmRequest,
                                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(aiUserProfileService.confirmExtraction(taskId, confirmRequest, loginUser));
    }

    @PostMapping("/profile-task/{taskId}/reject")
    public BaseResponse<AiProfileResponse> rejectProfileTask(@PathVariable String taskId,
                                                             HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(aiUserProfileService.rejectExtraction(taskId, loginUser));
    }
}
