package com.oa.workflow.approver.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.oa.authz.app.EffectivePermissionService;
import com.oa.common.audit.AuditLogWriter;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.form.app.FormDataService;
import com.oa.form.template.schema.FormSchemaService;
import com.oa.workflow.approver.api.dto.ApproverDtos.InstanceView;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>抄送「打开详情即已读」的调用点回归网</b>（AC-54 / TC-MSG-004）。
 *
 * <h2>被修的缺陷（2026-10-05）</h2>
 * <p>{@code FlowRuntimeMapper#markCcRead} 此前**没有任何调用点** —— 语句、XML、单测里的
 * 「SQL 存在性清单」都齐全，唯独没人调用它，于是 {@code flow_cc.read_at} 恒为 {@code NULL}、
 * 「抄送我的一览」的 {@code read} 恒为 {@code false}，而 TC-MSG-004 步骤③ 明确要求
 * 「{@code flow_cc.read_at} **在打开详情后**写入」。本类因此断言的是**调用关系**：读详情必须触发
 * 已读写入、且写入参数只能是「当前登录人 + 该实例」（真实的行级语义由
 * {@code FlowCcReadMySqlIntegrationTest} 在真实 MySQL 上断言）。
 *
 * <h2>不削弱的边界</h2>
 * <ul>
 *   <li><b>域外仍 404 且不写已读</b>：{@code requireInstance} 的域内判定（{@code @dataScope}
 *       织入，域外返回 {@code null} → 404）**在写入之前**，本类用「Mapper 返回 null」复现该形状；</li>
 *   <li><b>入口闸门不变</b>：无权限时不触实例读取、也不触已读写入。</li>
 * </ul>
 */
class FlowInstanceDetailCcReadTest {

    private static final long INSTANCE_ID = 9301L;

    /** 抄送人（同时也是能看详情的登录人）。 */
    private static final long CC_USER_ID = 507L;

    private FlowInstanceMapper instanceMapper;

    private FlowRuntimeMapper runtimeMapper;

    private FlowInstanceService service;

    @BeforeEach
    void setUp() {
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        when(permissions.permissionCodes(any()))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(FlowConfigPermission.FLOW_USE)));
        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);

        instanceMapper = mock(FlowInstanceMapper.class);
        runtimeMapper = mock(FlowRuntimeMapper.class);
        service = new FlowInstanceService(mock(ApproverPrecheckService.class), mock(ApproverDirectory.class),
                instanceMapper, mock(FlowTemplateMapper.class), gate, mock(AuditLogWriter.class),
                mock(FormSchemaService.class), mock(FormDataService.class), runtimeMapper, new OaProperties());

        DataScopeContext.set(DataScopeContext.builder()
                .principal(CurrentUser.of(CC_USER_ID, "cc01", "抄送人", "T507", 135L, 12L,
                        Set.of("employee"), Set.of(), false))
                .roleCodes(Set.of("employee"))
                .build());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private static FlowInstanceRow instance() {
        FlowInstanceRow row = new FlowInstanceRow();
        row.setId(INSTANCE_ID);
        row.setBizNo("OA-2026-930001");
        row.setFormType("matter");
        row.setInitiatorId(304L);
        row.setStatus("approving");
        row.setApproverSnapshotJson("{}");
        return row;
    }

    @Test
    @DisplayName("打开详情（GET /flow-instances/{id}）→ 以「当前登录人 + 该实例」调用 markCcRead 恰好一次")
    void detailMarksCcReadForCurrentUser() {
        when(instanceMapper.selectInstanceById(INSTANCE_ID)).thenReturn(instance());

        InstanceView view = service.detail(INSTANCE_ID);

        assertThat(view.id()).isEqualTo(INSTANCE_ID);
        assertThat(view.bizNo()).isEqualTo("OA-2026-930001");
        verify(runtimeMapper).markCcRead(INSTANCE_ID, CC_USER_ID);
    }

    @Test
    @DisplayName("已读写入**只**用当前登录人：非抄送人的访问不会以别人的 userId 写（行级语义见 MySQL 集成测试）")
    void detailNeverMarksOtherUsers() {
        when(instanceMapper.selectInstanceById(INSTANCE_ID)).thenReturn(instance());

        service.detail(INSTANCE_ID);

        verify(runtimeMapper, times(1)).markCcRead(anyLong(), anyLong());
        verify(runtimeMapper).markCcRead(INSTANCE_ID, CC_USER_ID);
        verifyNoMoreInteractions(runtimeMapper);
    }

    @Test
    @DisplayName("域外实例（数据域织入 → 读不到）：仍按 404 处理，且**一行已读都不写**")
    void detailOutsideDataScopeIs404AndWritesNothing() {
        when(instanceMapper.selectInstanceById(INSTANCE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(INSTANCE_ID))
                .isInstanceOf(BizException.class)
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(runtimeMapper, never()).markCcRead(any(), any());
    }
}
