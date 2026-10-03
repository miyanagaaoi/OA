package com.oa.workflow.definition.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.authz.app.EffectivePermissionService;
import com.oa.common.error.ErrorCode;
import com.oa.common.error.GlobalExceptionHandler;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.FlowDefinitionFixtures;
import com.oa.workflow.definition.app.FlowDefinitionService;
import com.oa.workflow.definition.app.TemplateLockQueryService;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.definition.infra.FlowNodeMapper;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * <b>模板生命周期接口的入口闸门（A / E 项，2026-10-04）</b> —— 「路由 → 权限码」的可执行对照表。
 *
 * <p>判据：{@code admin:flow:publish}（发布 / 归档 / <b>恢复</b>，同一个动作族）与
 * {@code admin:flow:template}（只读，含新的 {@code locked-by}）。
 *
 * <h2>为什么这里装配的是**真服务** + 假 mapper</h2>
 * <p>本域的权限判定在**服务层**（{@code WorkflowPermissionService} → {@code FlowConfigPermission}，
 * 见 {@code FlowDefinitionController} 的类注释），控制器只做入参绑定。因此「拒人发生在读库之前」
 * 这条口径只能靠「真服务 + 假 mapper + {@code verifyNoInteractions(mapper)}」证明：
 * 若闸门被挪到 mapper 之后，本类立刻红。
 */
class FlowDefinitionEntryGateTest {

    private static final long TEMPLATE_ID = 1L;

    private EffectivePermissionService permissions;
    private FlowTemplateMapper templateMapper;
    private FlowNodeMapper nodeMapper;
    private FlowInstanceMapper instanceMapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        permissions = mock(EffectivePermissionService.class);
        templateMapper = mock(FlowTemplateMapper.class);
        nodeMapper = mock(FlowNodeMapper.class);
        instanceMapper = mock(FlowInstanceMapper.class);
        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);
        FlowDefinitionService service = new FlowDefinitionService(templateMapper, nodeMapper, gate);
        TemplateLockQueryService lockQueryService =
                new TemplateLockQueryService(templateMapper, instanceMapper, gate);
        mvc = MockMvcBuilders.standaloneSetup(new FlowDefinitionController(service, lockQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private static CurrentUser user(long id, String account, String... roleCodes) {
        return CurrentUser.of(id, account, account, "T" + id, 135L, 12L, Set.of(roleCodes), Set.of(), false);
    }

    private void login(CurrentUser principal, String... permissionCodes) {
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(principal.roleCodes())
                .build());
        when(permissions.permissionCodes(principal.id())).thenReturn(Set.of(permissionCodes));
    }

    // ================================================================ 拒人侧

    @Test
    @DisplayName("入口层｜archive / restore：不持 admin:flow:publish → 403/40301，且**一行都没读**（mapper 零交互）")
    void lifecycleWritesRequirePublishPermission() throws Exception {
        // 主体持有 admin:flow（兜底码）与两个只读/节点码，但**没有** admin:flow:publish
        login(user(101L, "ca01", "company_admin"), FlowConfigPermission.FLOW_ADMIN, "flow",
                FlowConfigPermission.TEMPLATE_READ, FlowConfigPermission.NODE_WRITE);

        mvc.perform(post("/api/v1/flow-templates/" + TEMPLATE_ID + "/archive"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message")
                        .value("无权执行「发布/归档流程模板版本」：需要权限 " + FlowConfigPermission.PUBLISH));

        mvc.perform(post("/api/v1/flow-templates/" + TEMPLATE_ID + "/restore"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(templateMapper, nodeMapper);
    }

    @Test
    @DisplayName("入口层｜GET locked-by：不持 admin:flow:template → 403，且实例 mapper 零交互")
    void lockedByRequiresTemplateRead() throws Exception {
        login(user(102L, "pm01", "employee"), FlowConfigPermission.PUBLISH, "flow");

        mvc.perform(get("/api/v1/flow-templates/" + TEMPLATE_ID + "/locked-by"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(instanceMapper, templateMapper);
    }

    @Test
    @DisplayName("入口层｜未登录 → 401（闸门读不到 principal）")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/flow-templates/" + TEMPLATE_ID + "/restore"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    // ================================================================ 放行侧

    @Test
    @DisplayName("入口层｜archive / restore：持 admin:flow:publish → 走到状态机（归档成功 / 恢复成功）")
    void lifecycleWritesPassWithPublishPermission() throws Exception {
        login(user(101L, "flowadmin", "employee"), FlowConfigPermission.PUBLISH);

        // 归档：同 code 另有 published 版本 → 放行（唯一 published 的守卫见 FlowTemplateArchiveRestoreTest）
        when(templateMapper.selectTemplateById(TEMPLATE_ID))
                .thenReturn(FlowDefinitionFixtures.matter(TEMPLATE_ID, 1, "published"));
        when(templateMapper.selectByCode("matter")).thenReturn(List.of(
                FlowDefinitionFixtures.matter(2L, 2, "published"),
                FlowDefinitionFixtures.matter(TEMPLATE_ID, 1, "published")));

        mvc.perform(post("/api/v1/flow-templates/" + TEMPLATE_ID + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(templateMapper).updateStatus(TEMPLATE_ID, "archived", 101L);

        // 恢复：archived 且无其它 published → 放行（并跑发布前校验，故要给出合法节点）
        when(templateMapper.selectTemplateById(TEMPLATE_ID))
                .thenReturn(FlowDefinitionFixtures.matter(TEMPLATE_ID, 1, "archived"));
        when(templateMapper.selectByCode("matter"))
                .thenReturn(List.of(FlowDefinitionFixtures.matter(TEMPLATE_ID, 1, "archived")));
        when(nodeMapper.selectByTemplateId(TEMPLATE_ID))
                .thenAnswer(invocation -> new ArrayList<>(FlowDefinitionFixtures.matterNodes(TEMPLATE_ID)));
        when(nodeMapper.countByTemplateId(any())).thenReturn(7);

        mvc.perform(post("/api/v1/flow-templates/" + TEMPLATE_ID + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(templateMapper).updateStatus(TEMPLATE_ID, "published", 101L);
    }

    @Test
    @DisplayName("入口层｜GET locked-by：持 admin:flow:template → 200 且路由已存在（改前 404）")
    void lockedByRouteExists() throws Exception {
        login(user(103L, "flowadmin2", "employee"), FlowConfigPermission.TEMPLATE_READ);
        when(templateMapper.selectTemplateById(TEMPLATE_ID))
                .thenReturn(FlowDefinitionFixtures.matterV1());
        when(instanceMapper.selectInFlightByTemplate(TEMPLATE_ID, 1)).thenReturn(List.of());

        mvc.perform(get("/api/v1/flow-templates/" + TEMPLATE_ID + "/locked-by"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        verify(instanceMapper).selectInFlightByTemplate(TEMPLATE_ID, 1);
    }

    @Test
    @DisplayName("入口层｜系统管理员（isSuperAdmin）即使有效权限码为空：archive / restore / locked-by 全部放行")
    void superAdminPasses() throws Exception {
        login(user(100L, "admin", "admin"));
        when(templateMapper.selectTemplateById(TEMPLATE_ID))
                .thenReturn(FlowDefinitionFixtures.matter(TEMPLATE_ID, 1, "archived"));
        when(templateMapper.selectByCode("matter"))
                .thenReturn(List.of(FlowDefinitionFixtures.matter(TEMPLATE_ID, 1, "archived")));
        when(nodeMapper.selectByTemplateId(TEMPLATE_ID))
                .thenAnswer(invocation -> new ArrayList<>(FlowDefinitionFixtures.matterNodes(TEMPLATE_ID)));
        when(nodeMapper.countByTemplateId(any())).thenReturn(7);

        mvc.perform(post("/api/v1/flow-templates/" + TEMPLATE_ID + "/archive")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/flow-templates/" + TEMPLATE_ID + "/restore")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/flow-templates/" + TEMPLATE_ID + "/locked-by")).andExpect(status().isOk());
    }

    // ================================================================ 对照表锁

    @Test
    @DisplayName("对照表锁｜模板生命周期 3 条写路由 + 1 条读路由 → 权限码逐行锁定（防以后漂移）")
    void entryGateTableIsLocked() {
        assertThat(FlowConfigPermission.PUBLISH)
                .as("归档 / 恢复（与 publish 同族）共用 admin:flow:publish")
                .isEqualTo("admin:flow:publish");
        assertThat(FlowConfigPermission.TEMPLATE_READ)
                .as("locked-by 是只读视图，取 admin:flow:template")
                .isEqualTo("admin:flow:template");
    }
}
