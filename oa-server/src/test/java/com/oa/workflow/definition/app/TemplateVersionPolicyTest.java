package com.oa.workflow.definition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.TemplateStatus;
import com.oa.workflow.definition.domain.FlowTemplate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 模板版本状态机与「在途锁版本」判定单测（templates.md V-01/V-02/V-05/V-08 + §3.3 + §4.1/§4.3）。
 */
class TemplateVersionPolicyTest {

    @Test
    @DisplayName("只有草稿可编辑；已发布 / 已归档一律 409（FR-§4.3「不得直接修改已发布版本」）")
    void onlyDraftIsEditable() {
        TemplateVersionPolicy.assertEditable(FlowDefinitionFixtures.matter(1L, 2, "draft"));

        for (String status : List.of("published", "archived")) {
            assertThatThrownBy(() -> TemplateVersionPolicy.assertEditable(
                    FlowDefinitionFixtures.matter(1L, 1, status)))
                    .as("状态 %s 应只读", status)
                    .isInstanceOf(BizException.class)
                    .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.FLOW_DEFINITION_IMMUTABLE));
        }
    }

    @Test
    @DisplayName("版本号单调递增：nextVersion = 最大版本 + 1；复用版本号被拒（V-08）")
    void versionNumbersAreMonotonic() {
        List<FlowTemplate> history = List.of(
                FlowDefinitionFixtures.matter(3L, 3, "published"),
                FlowDefinitionFixtures.matter(1L, 1, "archived"),
                FlowDefinitionFixtures.matter(2L, 2, "archived"));

        assertThat(TemplateVersionPolicy.nextVersion(history)).isEqualTo(4);
        assertThat(TemplateVersionPolicy.nextVersion(List.of())).isEqualTo(1);

        TemplateVersionPolicy.assertVersionFresh(history, 4);
        assertThatThrownBy(() -> TemplateVersionPolicy.assertVersionFresh(history, 3))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("版本号不得复用");
    }

    @Test
    @DisplayName("同一单据类型下最多一个草稿 / 一个已发布；版本历史按版本倒序")
    void singleDraftAndSinglePublished() {
        List<FlowTemplate> history = List.of(
                FlowDefinitionFixtures.matter(3L, 3, "draft"),
                FlowDefinitionFixtures.matter(2L, 2, "published"),
                FlowDefinitionFixtures.matter(1L, 1, "archived"));

        assertThat(TemplateVersionPolicy.draft(history).getVersion()).isEqualTo(3);
        assertThat(TemplateVersionPolicy.published(history).getVersion()).isEqualTo(2);
        assertThat(TemplateVersionPolicy.byVersionDesc(history))
                .extracting(FlowTemplate::getVersion).containsExactly(3, 2, 1);
        assertThat(TemplateVersionPolicy.draft(List.of())).isNull();
    }

    @Test
    @DisplayName("在途锁版本读取口径：按 (code, version) 取；版本缺失即报错（§4.3 禁止删除历史版本）")
    void lockedVersionRead() {
        List<FlowTemplate> history = List.of(
                FlowDefinitionFixtures.matter(2L, 2, "published"),
                FlowDefinitionFixtures.matter(1L, 1, "archived"));

        FlowTemplate locked = TemplateVersionPolicy.requireLocked(history, 1, "matter");
        assertThat(locked.getId()).isEqualTo(1L);
        assertThat(locked.getStatus()).isEqualTo("archived");

        assertThatThrownBy(() -> TemplateVersionPolicy.requireLocked(history, 9, "matter"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("在途实例锁定的模板版本不存在");
    }

    @Test
    @DisplayName("新实例只能使用 published（draft / archived 都不可用于发起，V-05）")
    void onlyPublishedUsableForNewInstance() {
        TemplateVersionPolicy.assertUsableForNewInstance(FlowDefinitionFixtures.matter(1L, 1, "published"));
        for (String status : List.of("draft", "archived")) {
            assertThatThrownBy(() -> TemplateVersionPolicy.assertUsableForNewInstance(
                    FlowDefinitionFixtures.matter(1L, 1, status)))
                    .isInstanceOf(BizException.class);
        }
        assertThat(TemplateStatus.PUBLISHED.usableByNewInstance()).isTrue();
        assertThat(TemplateStatus.DRAFT.readOnly()).isFalse();
        assertThat(TemplateStatus.ARCHIVED.readOnly()).isTrue();
    }

    @Test
    @DisplayName("归档只阻止新实例：archived 版本仍可被在途实例锁定读取（V-05）")
    void archivedStillReadableForInFlight() {
        FlowTemplate archived = FlowDefinitionFixtures.matter(1L, 1, "archived");
        assertThat(archived.statusEnum()).isEqualTo(TemplateStatus.ARCHIVED);
        assertThat(archived.statusEnum().usableByNewInstance()).isFalse();
        // 在途实例只持有 template_id，因此读取不受状态影响
        assertThat(TemplateVersionPolicy.requireLocked(List.of(archived), 1, "matter").getId()).isEqualTo(1L);
    }
}
