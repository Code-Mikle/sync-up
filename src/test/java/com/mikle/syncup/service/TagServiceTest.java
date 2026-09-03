package com.mikle.syncup.service;

import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.model.vo.TagCategoryVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TagServiceTest {

    @Resource
    protected TagService tagService;

    @Resource
    protected DataSource dataSource;

    @BeforeEach
    protected void ensureUsingTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String databaseName = connection.getCatalog();

            Assertions.assertEquals(
                    "sync_up_test",
                    databaseName,
                    "当前连接的不是 sync_up_test，已停止测试，避免污染开发数据库"
            );
        }
    }

    @Test
    void listEnabledCategories_shouldReturnOnlyEnabledCategoriesAndTagsInConfiguredOrder() {
        List<TagCategoryVO> tagCategoryVOS = tagService.listEnabledCategories();
    }

    @Test
    @Transactional
    void validateEnabledTagIds_whenAllTagsAreEnabled_shouldReturnUniqueTags() {
        // Arrange
        Tag tagA = createTestTag(1);
        Tag tagB = createTestTag(1);

        // Act
        List<Tag> result = tagService.validateEnabledTagIds(
                List.of(tagA.getId(), tagB.getId(), tagA.getId())
        );

        // Assert
        Set<Long> actualTagIds = result.stream()
                .map(Tag::getId)
                .collect(Collectors.toSet());

        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(
                Set.of(tagA.getId(), tagB.getId()),
                actualTagIds
        );
    }

    @Test
    @Transactional
    void validateEnabledTagIds_whenTagIsMissingOrDisabled_shouldBeRejected() {
        // Arrange
        Tag tagA = createTestTag(0);
        Tag tagB = createTestTag(1);

        // Act
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> tagService.validateEnabledTagIds(
                        List.of(tagA.getId(), tagB.getId(), 1111L)
                )
        );

        // Assert
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        Assertions.assertEquals("标签不存在或已禁用", exception.getDescription());
    }

    @Test
    @Transactional
    void serializeAndValidateTagIds_shouldDeduplicateAndPreserveInputOrder() {
        Tag tagA = createTestTag(1);
        Tag tagB = createTestTag(1);

        String result = tagService.serializeAndValidateTagIds(
                List.of(tagB.getId(), tagA.getId(), tagB.getId()));

        Assertions.assertEquals("[" + tagB.getId() + "," + tagA.getId() + "]", result);
        Assertions.assertEquals("[]", tagService.serializeAndValidateTagIds(List.of()));
    }


    private Tag createTestTag(int status) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);

        Tag tag = Tag.builder()
                .categoryId(1L)
                .code("test_tag_" + suffix)
                .name("测试标签_" + suffix)
                .description("TagService 测试数据")
                .status(status)
                .sortOrder(0)
                .build();

        boolean saved = tagService.save(tag);
        Assertions.assertTrue(saved, "test tag should be created");
        return tag;
    }

}
