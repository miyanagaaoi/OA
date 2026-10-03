package com.oa.workflow.definition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.error.BizException;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.LockedInstanceRow;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.LockedInstanceView;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>{@code GET /flow-templates/{id}/locked-by} 的服务编排（E 项，2026-10-04）</b>。
 *
 * <p>本类锁的是「取哪一行模板版本 + 把行映射成出参 + 未见模板 404」；
 * 「行集是否真的受调用人数据域约束」属 SQL 层，由
 * {@code TemplateLockQueryMySqlIntegrationTest}（真库）与 {@code WorkflowMapperXmlTest}（标记纪律）承担。
 */
class TemplateLockQueryServiceTest {

    private static final Long TEMPLATE_ID = 1L;

    private FlowTemplateMapper templateMapper;
    private FlowInstanceMapper instanceMapper;
    private TemplateLockQueryService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(FlowTemplateMapper.class);
        instanceMapper = mock(FlowInstanceMapper.class);
        WorkflowPermissionService permissionService = mock(WorkflowPermissionService.class);
        when(permissionService.requireTemplateRead()).thenReturn(CurrentUser.of(1L, "admin", "系统管理员",
                "A001", 1L, 1L, Set.of("admin"), Set.of(DataScopeType.GROUP_ALL), false));
        service = new TemplateLockQueryService(templateMapper, instanceMapper, permissionService);
        when(templateMapper.selectTemplateById(TEMPLATE_ID)).thenReturn(FlowDefinitionFixtures.matterV1());
    }

    private static LockedInstanceRow row(long id, String bizNo, Integer version, String initiatorName) {
        LockedInstanceRow row = new LockedInstanceRow();
        row.setInstanceId(id);
        row.setBizNo(bizNo);
        row.setTemplateId(TEMPLATE_ID);
        row.setTemplateVersion(version);
        row.setStatus("approving");
        row.setInitiatorId(501L);
        row.setInitiatorName(initiatorName);
        row.setCurrentNodeSeq(2);
        row.setCurrentNodeName("财务部复核");
        row.setSubmittedAt("2026-10-04 10:00:00");
        return row;
    }

    @Test
    @DisplayName("在途锁版本：默认取该模板行自身的版本号，行集原样映射为出参（单号/发起人/当前节点）")
    void mapsRowsUsingTemplateVersion() {
        when(instanceMapper.selectInFlightByTemplate(TEMPLATE_ID, 1))
                .thenReturn(List.of(row(9L, "OA-2026-000009", 1, "员工甲"),
                        row(8L, "OA-2026-000008", 1, "员工乙")));

        List<LockedInstanceView> views = service.lockedBy(TEMPLATE_ID, null);

        assertThat(views).hasSize(2);
        LockedInstanceView first = views.get(0);
        assertThat(first.instanceId()).isEqualTo(9L);
        assertThat(first.bizNo()).isEqualTo("OA-2026-000009");
        assertThat(first.templateVersion()).isEqualTo(1);
        assertThat(first.status()).isEqualTo("approving");
        assertThat(first.initiatorId()).isEqualTo(501L);
        assertThat(first.initiatorName()).isEqualTo("员工甲");
        assertThat(first.currentNodeSeq()).isEqualTo(2);
        assertThat(first.currentNodeName()).isEqualTo("财务部复核");
        assertThat(first.submittedAt()).isEqualTo("2026-10-04 10:00:00");
        verify(instanceMapper).selectInFlightByTemplate(TEMPLATE_ID, 1);
    }

    @Test
    @DisplayName("在途锁版本：显式 templateVersion 优先；查无实例 = 空数组（不是 404）")
    void explicitVersionAndEmptyResult() {
        when(instanceMapper.selectInFlightByTemplate(TEMPLATE_ID, 3)).thenReturn(List.of());

        assertThat(service.lockedBy(TEMPLATE_ID, 3)).isEmpty();
        verify(instanceMapper).selectInFlightByTemplate(TEMPLATE_ID, 3);
    }

    @Test
    @DisplayName("在途锁版本：模板不存在 → 404（不落到实例查询）")
    void unknownTemplateIsNotFound() {
        when(templateMapper.selectTemplateById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.lockedBy(404L, null)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("在途锁版本：mapper 返回 null 也按空数组处理（出参永不为 null）")
    void nullRowsBecomeEmptyList() {
        when(instanceMapper.selectInFlightByTemplate(TEMPLATE_ID, 1)).thenReturn(null);

        assertThat(service.lockedBy(TEMPLATE_ID, null)).isEmpty();
    }
}
