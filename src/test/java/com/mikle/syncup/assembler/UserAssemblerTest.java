package com.mikle.syncup.assembler;

import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.UserSearchResultVO;
import com.mikle.syncup.model.vo.UserVO;
import com.mikle.syncup.service.TagService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAssemblerTest {

    private final TagService tagService = mock(TagService.class);
    private final UserAssembler userAssembler = new UserAssembler(tagService);

    @Test
    void shouldMapAuthenticatedCurrentAndPublicUsersWithExplicitFields() {
        Date updateTime = new Date();
        User source = User.builder()
                .id(100L)
                .username("测试用户")
                .userAccount("test_account")
                .avatarUrl("https://example.com/avatar.png")
                .gender(0)
                .userPassword("encoded-password")
                .phone("13800000000")
                .email("test@example.com")
                .city("成都")
                .tagIds("[107]")
                .profile("周末打羽毛球")
                .userStatus(0)
                .updateTime(updateTime)
                .isDelete(0)
                .userRole(0)
                .build();
        when(tagService.parseTagIds("[107]")).thenReturn(List.of(107L));
        when(tagService.toDisplayTagNames("[107]")).thenReturn(List.of("羽毛球"));

        User authenticatedUser = userAssembler.toAuthenticatedUser(source);
        UserVO currentUser = userAssembler.toCurrentUserVO(source);
        UserSearchResultVO publicUser = userAssembler.toPublicUserVO(source);

        Assertions.assertNull(authenticatedUser.getUserPassword());
        Assertions.assertNull(authenticatedUser.getIsDelete());
        Assertions.assertEquals(updateTime, authenticatedUser.getUpdateTime());

        Assertions.assertEquals("test_account", currentUser.getUserAccount());
        Assertions.assertEquals("13800000000", currentUser.getPhone());
        Assertions.assertEquals(List.of(107L), currentUser.getTagIds());
        Assertions.assertEquals(List.of("羽毛球"), currentUser.getTagNames());

        Assertions.assertEquals(source.getId(), publicUser.getId());
        Assertions.assertEquals(source.getUsername(), publicUser.getUsername());
        Assertions.assertEquals(List.of("羽毛球"), publicUser.getTagNames());
    }
}
