package com.mikle.syncup.ai;

import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.vo.TeamDraftVO;
import com.mikle.syncup.ai.tool.CreateTeamDraftTool;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.function.Consumer;

class CreateTeamDraftToolTest {

    private final CreateTeamDraftTool tool = new CreateTeamDraftTool();

    @Test
    void execute_validIntent_shouldBuildDraftWithoutChangingInput() {
        TeamIntent intent = validIntent();
        long expectedExpiryLowerBound = System.currentTimeMillis() + 29 * 60 * 1000L;

        AiToolResult result = tool.execute(intent, loginUser());

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(CreateTeamDraftTool.TOOL_NAME, result.getToolName());
        Assertions.assertEquals("draft", result.getType());
        TeamDraftVO draft = Assertions.assertInstanceOf(TeamDraftVO.class, result.getData());
        Assertions.assertAll(
                () -> Assertions.assertNotNull(draft.getDraftId()),
                () -> Assertions.assertEquals("周末羽毛球", draft.getName()),
                () -> Assertions.assertEquals("新手友好", draft.getDescription()),
                () -> Assertions.assertEquals(4, draft.getMaxNum()),
                () -> Assertions.assertEquals(1, draft.getActivityCategory()),
                () -> Assertions.assertEquals("羽毛球", draft.getActivityType()),
                () -> Assertions.assertEquals("西安", draft.getCity()),
                () -> Assertions.assertEquals("雁塔区", draft.getDistrict()),
                () -> Assertions.assertEquals(new BigDecimal("50.00"), draft.getBudgetPerPerson()),
                () -> Assertions.assertEquals("入门", draft.getSkillLevel()),
                () -> Assertions.assertTrue(draft.getExpiresAt().getTime() >= expectedExpiryLowerBound),
                () -> Assertions.assertTrue(draft.getExpiresAt().getTime()
                        <= System.currentTimeMillis() + 31 * 60 * 1000L)
        );
    }

    @Test
    void execute_missingOptionalText_shouldUseFactBasedDefaults() {
        TeamIntent intent = validIntent();
        intent.setTeamName(" ");
        intent.setDescription(null);

        AiToolResult result = tool.execute(intent, loginUser());

        TeamDraftVO draft = Assertions.assertInstanceOf(TeamDraftVO.class, result.getData());
        Assertions.assertTrue(result.isSuccess());
        System.out.println("draft name = " + draft.getName());
        Assertions.assertEquals("西安羽毛球搭子队", draft.getName());
        Assertions.assertTrue(draft.getDescription().contains("确认前不会写入业务表"));
    }

    @Test
    void execute_invalidBusinessFields_shouldReturnFailure() {
        assertInvalid(intent -> intent.setActivityCategory(9), "活动大类");
        assertInvalid(intent -> intent.setCity(" "), "城市");
        assertInvalid(intent -> intent.setMemberCount(0), "1 到 20");
        assertInvalid(intent -> intent.setMemberCount(21), "1 到 20");
        assertInvalid(intent -> intent.setBudgetMax(new BigDecimal("-0.01")), "预算");
        assertInvalid(intent -> intent.setStartTime(new Date(System.currentTimeMillis() - 60_000)), "开始时间");
    }

    @Test
    void execute_withoutLoginUser_shouldRejectRequest() {
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> tool.execute(validIntent(), null)
        );

        Assertions.assertEquals(ErrorCode.NOT_LOGIN.getCode(), exception.getCode());
    }

    private void assertInvalid(Consumer<TeamIntent> mutation, String expectedSummaryPart) {
        TeamIntent intent = validIntent();
        mutation.accept(intent);

        AiToolResult result = tool.execute(intent, loginUser());

        Assertions.assertFalse(result.isSuccess());
        Assertions.assertNull(result.getData());
        Assertions.assertTrue(result.getSummary().contains(expectedSummaryPart));
    }

    private TeamIntent validIntent() {
        TeamIntent intent = new TeamIntent();
        intent.setActivityCategory(1);
        intent.setActivityType("羽毛球");
        intent.setCity("西安");
        intent.setDistrict("雁塔区");
        intent.setStartTime(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000L));
        intent.setDurationMinutes(120);
        intent.setMemberCount(4);
        intent.setTeamName("周末羽毛球");
        intent.setDescription("新手友好");
        intent.setBudgetMax(new BigDecimal("50.00"));
        intent.setSkillLevel("入门");
        return intent;
    }

    private User loginUser() {
        User user = new User();
        user.setId(1001L);
        return user;
    }
}
