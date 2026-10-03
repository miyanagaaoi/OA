package com.oa.workflow.definition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.CheckItemView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.CheckRuleView;
import com.oa.workflow.definition.infra.FlowNodeMapper;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>{@code checks[]} / {@code check-rules} 的顺序稳定性（D 项，2026-10-04）</b>。
 *
 * <h2>被修的缺陷</h2>
 * <p>规则清单原本用 {@code Map.copyOf(LinkedHashMap)} 收尾 —— {@code Map.copyOf} 返回
 * {@code ImmutableCollections.MapN}，迭代顺序由**哈希与内部 SALT** 决定，与插入顺序无关。
 * 运行期实测 {@code GET /flow-designs/check-rules} 的顺序是
 * {@code R-METADATA, R-THRESHOLD, R-SEQ, …}（既不是声明序，也不能跨进程复现），
 * 设计器渲染的「校验清单」与 dry-run 报告因此对不上文档。
 *
 * <h2>权威顺序（templates.md 的规则编号序）</h2>
 * <p>{@code R-METADATA → R-TRUNK → R-SEQ → R-NODE-CODE → R-NODE-TYPE → R-APPROVER-RULE →
 * R-DECISION → R-THRESHOLD → R-SIGN → R-TIMEOUT → R-SKIP → R-GATE}。
 */
class PrePublishCheckerOrderTest {

    private static final List<String> AUTHORITATIVE_ORDER = List.of(
            "R-METADATA", "R-TRUNK", "R-SEQ", "R-NODE-CODE", "R-NODE-TYPE", "R-APPROVER-RULE",
            "R-DECISION", "R-THRESHOLD", "R-SIGN", "R-TIMEOUT", "R-SKIP", "R-GATE");

    @Test
    @DisplayName("规则清单：RULES 的迭代顺序 = 权威声明序（Map.copyOf 会丢序，故必须是 LinkedHashMap）")
    void rulesKeepDeclarationOrder() {
        assertThat(new ArrayList<>(PrePublishChecker.RULES.keySet()))
                .containsExactlyElementsOf(AUTHORITATIVE_ORDER);
    }

    @Test
    @DisplayName("规则清单：仍然只读（保序不等于可写）")
    void rulesRemainUnmodifiable() {
        assertThatThrownBy(() -> PrePublishChecker.RULES.put("R-X", "x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> PrePublishChecker.RULES.remove("R-SEQ"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("dry-run 报告：checks[] 的顺序与规则清单逐项一致（同一份 RULES，不另算一遍）")
    void reportChecksFollowTheSameOrder() {
        PrePublishReport report = PrePublishChecker.run(FlowDefinitionFixtures.matterV1(),
                FlowDefinitionFixtures.matterNodes(1L));

        assertThat(report.checks()).extracting(PrePublishReport.Check::rule)
                .containsExactlyElementsOf(AUTHORITATIVE_ORDER);
        assertThat(report.passed()).isTrue();
    }

    @Test
    @DisplayName("接口出参：GET /flow-designs/check-rules 的顺序 = checks[] 的顺序（设计器两侧同序）")
    void serviceCheckRulesFollowTheSameOrder() {
        WorkflowPermissionService permissionService = mock(WorkflowPermissionService.class);
        CurrentUser operator = CurrentUser.of(1L, "admin", "系统管理员", "A001", 1L, 1L, Set.of("admin"),
                Set.of(DataScopeType.GROUP_ALL), false);
        when(permissionService.requireTemplateRead()).thenReturn(operator);
        FlowDefinitionService service = new FlowDefinitionService(mock(FlowTemplateMapper.class),
                mock(FlowNodeMapper.class), permissionService);

        assertThat(service.checkRules()).extracting(CheckRuleView::rule)
                .containsExactlyElementsOf(AUTHORITATIVE_ORDER);

        FlowTemplateMapper templateMapper = mock(FlowTemplateMapper.class);
        FlowNodeMapper nodeMapper = mock(FlowNodeMapper.class);
        when(templateMapper.selectTemplateById(1L)).thenReturn(FlowDefinitionFixtures.matterV1());
        when(nodeMapper.selectByTemplateId(any())).thenReturn(FlowDefinitionFixtures.matterNodes(1L));
        FlowDefinitionService reporting = new FlowDefinitionService(templateMapper, nodeMapper,
                permissionService);

        assertThat(reporting.prePublishCheck(1L).checks()).extracting(CheckItemView::rule)
                .containsExactlyElementsOf(AUTHORITATIVE_ORDER);
    }
}
