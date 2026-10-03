package com.oa.workflow.definition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.PublishRequest;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowNodeMapper;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>模板归档守卫与恢复路径（A 项，2026-10-04）</b>。
 *
 * <h2>被修的缺陷（运行期已误触发过一次）</h2>
 * <p>改前 {@code POST /flow-templates/{id}/archive} 对 {@code published} 版本直接返回 200，
 * 且**不校验**它是否是该 {@code code} 下唯一的 {@code published} 版本 —— 一次误点就让该类单据
 * 无法发起新实例（templates.md §3.3 / V-05 的反面），而 §4.3 又禁止删除已产生的模板版本，
 * 只能靠「开新草稿 → 发布」救回。
 *
 * <h2>本类覆盖的正面口径</h2>
 * <ul>
 *   <li>唯一 {@code published} 归档 → 409 / {@code 40914}，且**一行都不写**（{@code updateStatus} 未被调用）；</li>
 *   <li>同 code 另有 {@code published} → 可归档；{@code draft}（放弃草稿，40907 文案给出的路径）→ 可归档；
 *       {@code archived} 幂等返回；状态非法 → 400；</li>
 *   <li>恢复：{@code archived → published}（含"恢复也要过发布前校验"）；已有 {@code published} → 409 / {@code 40915}；
 *       {@code published} 幂等；{@code draft} → 409。</li>
 * </ul>
 *
 * <p>越权（不持 {@code admin:flow:publish}）在入口层判定，见 {@code FlowDefinitionEntryGateTest}。
 */
class FlowTemplateArchiveRestoreTest {

    private static final Long V1_ID = 1L;
    private static final Long V2_ID = 2L;

    private FlowTemplateMapper templateMapper;
    private FlowNodeMapper nodeMapper;
    private FlowDefinitionService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(FlowTemplateMapper.class);
        nodeMapper = mock(FlowNodeMapper.class);

        WorkflowPermissionService permissionService = mock(WorkflowPermissionService.class);
        CurrentUser operator = CurrentUser.of(1L, "admin", "系统管理员", "A001", 1L, 1L, Set.of("admin"),
                Set.of(DataScopeType.GROUP_ALL), false);
        when(permissionService.requirePublish()).thenReturn(operator);
        when(permissionService.requireTemplateRead()).thenReturn(operator);
        when(permissionService.isSuperAdmin(any())).thenReturn(true);

        when(nodeMapper.selectByTemplateId(anyLong()))
                .thenAnswer(invocation -> new ArrayList<>(FlowDefinitionFixtures.matterNodes(
                        invocation.getArgument(0))));
        when(nodeMapper.countByTemplateId(anyLong()))
                .thenAnswer(invocation -> FlowDefinitionFixtures.matterNodes(null).size());

        service = new FlowDefinitionService(templateMapper, nodeMapper, permissionService);
    }

    // ================================================================ 归档守卫

    @Test
    @DisplayName("归档守卫｜唯一 published 版本 → 409/40914「请先发布新版本再归档旧版本」，且不写库")
    void archiveRejectsTheOnlyPublishedVersion() {
        FlowTemplate published = FlowDefinitionFixtures.matterV1();
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(published);
        when(templateMapper.selectByCode("matter")).thenReturn(List.of(published));

        assertThatThrownBy(() -> service.archive(V1_ID, new PublishRequest("误点")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("唯一的已发布版本")
                .hasMessageContaining("请先发布新版本再归档旧版本")
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FLOW_LAST_PUBLISHED_ARCHIVE_DENIED));

        verify(templateMapper, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("归档守卫｜同 code 另有 published 版本 → 允许归档（归档后该类单据仍可发起）")
    void archiveAllowedWhenAnotherPublishedVersionExists() {
        FlowTemplate older = FlowDefinitionFixtures.matter(V1_ID, 1, "published");
        FlowTemplate newer = FlowDefinitionFixtures.matter(V2_ID, 2, "published");
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(older);
        when(templateMapper.selectByCode("matter")).thenReturn(List.of(newer, older));

        service.archive(V1_ID, new PublishRequest("已有 v2 在线"));

        verify(templateMapper).updateStatus(V1_ID, "archived", 1L);
    }

    @Test
    @DisplayName("归档守卫｜draft 仍可归档（放弃草稿：40907 文案「请先发布或归档该草稿」的另一半）")
    void archiveStillAllowsDraftDiscard() {
        FlowTemplate draft = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(draft);

        service.archive(V2_ID, null);

        verify(templateMapper).updateStatus(V2_ID, "archived", 1L);
    }

    @Test
    @DisplayName("归档守卫｜archived 幂等（不重复写库、不重复审计），状态非法 → 400")
    void archiveIsIdempotentAndRejectsUnknownStatus() {
        FlowTemplate archived = FlowDefinitionFixtures.matter(V1_ID, 1, "archived");
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(archived);

        assertThat(service.archive(V1_ID, null).status()).isEqualTo("archived");
        verify(templateMapper, never()).updateStatus(any(), any(), any());

        FlowTemplate broken = FlowDefinitionFixtures.matter(V2_ID, 2, "retired");
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(broken);
        assertThatThrownBy(() -> service.archive(V2_ID, null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FLOW_DEFINITION_INVALID));
    }

    // ================================================================ 恢复

    @Test
    @DisplayName("恢复｜archived 且该 code 无 published → archived → published（版本号与配置不变）")
    void restoreArchivedVersion() {
        FlowTemplate archived = FlowDefinitionFixtures.matter(V1_ID, 1, "archived");
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(archived);
        when(templateMapper.selectByCode("matter")).thenReturn(List.of(archived));

        service.restore(V1_ID, new PublishRequest("误归档恢复"));

        verify(templateMapper).updateStatus(V1_ID, "published", 1L);
        verify(templateMapper).updateNodeCount(V1_ID, 7, 1L);
        verify(templateMapper, never()).archiveOtherPublished(any(), any(), any());
    }

    @Test
    @DisplayName("恢复｜该 code 已有 published 版本 → 409/40915（§3.3 同一 code 最多一个 published），不写库")
    void restoreRejectsWhenAnotherPublishedVersionExists() {
        FlowTemplate archived = FlowDefinitionFixtures.matter(V1_ID, 1, "archived");
        FlowTemplate published = FlowDefinitionFixtures.matter(V2_ID, 2, "published");
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(archived);
        when(templateMapper.selectByCode("matter")).thenReturn(List.of(published, archived));

        assertThatThrownBy(() -> service.restore(V1_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已有已发布版本")
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FLOW_RESTORE_CONFLICT));

        verify(templateMapper, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("恢复｜published 幂等；draft → 409（只有 archived 可恢复）")
    void restoreStatusRules() {
        FlowTemplate published = FlowDefinitionFixtures.matterV1();
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(published);
        assertThat(service.restore(V1_ID, null).status()).isEqualTo("published");
        verify(templateMapper, never()).updateStatus(any(), any(), any());

        FlowTemplate draft = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(draft);
        assertThatThrownBy(() -> service.restore(V2_ID, null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FLOW_DEFINITION_IMMUTABLE));
    }

    @Test
    @DisplayName("恢复｜配置校验不通过 → 400 且不写库（草稿可被直接归档 ⇒ archived 里可能有从未校验过的配置）")
    void restoreRunsPrePublishValidation() {
        FlowTemplate archived = FlowDefinitionFixtures.matter(V1_ID, 1, "archived");
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(archived);
        when(templateMapper.selectByCode("matter")).thenReturn(List.of(archived));
        List<com.oa.workflow.definition.domain.FlowNode> broken =
                new ArrayList<>(FlowDefinitionFixtures.matterNodes(V1_ID));
        broken.remove(4); // 丢掉 ⑤ → R-TRUNK / R-SEQ 都会失败
        when(nodeMapper.selectByTemplateId(V1_ID)).thenReturn(broken);

        assertThatThrownBy(() -> service.restore(V1_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("发布被拒绝");

        verify(templateMapper, never()).updateStatus(any(), any(), any());
    }
}
