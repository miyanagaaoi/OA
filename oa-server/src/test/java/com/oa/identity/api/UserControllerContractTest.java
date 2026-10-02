package com.oa.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.config.JacksonConfig;
import com.oa.common.config.OaProperties;
import com.oa.identity.api.dto.InFlightDtos;
import com.oa.identity.api.dto.PositionDtos;
import com.oa.identity.api.dto.UserDtos;
import com.oa.identity.app.OrgLeaderService;
import com.oa.identity.app.PositionService;
import com.oa.identity.app.UserService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * 人员接口的**契约单测**（施工要求第 2/3/5 条的接线部分）：
 * <ol>
 *   <li>分页参数：规范名 {@code size} 优先，旧别名 {@code pageSize} 兜底，都缺省 20；</li>
 *   <li>{@code GET /users/{id}/in-flight-check} 委托到服务层（出参形状由服务层保证）；</li>
 *   <li>{@code PUT /users/{id}/positions/{positionId}} 委托到 {@code PositionService#update}。</li>
 * </ol>
 */
class UserControllerContractTest {

    private final UserService userService = mock(UserService.class);
    private final PositionService positionService = mock(PositionService.class);
    private final OrgLeaderService leaderService = mock(OrgLeaderService.class);
    private final UserController controller = new UserController(userService, positionService, leaderService);

    @Test
    @DisplayName("size 与 pageSize 同时出现 → 以 size 为准")
    void sizeWinsOverLegacyAlias() {
        assertThat(UserController.resolvePageSize(50L, 10L)).isEqualTo(50L);
    }

    @Test
    @DisplayName("只传旧别名 pageSize → 兼容；都不传 → 默认 20")
    void legacyAliasAndDefault() {
        assertThat(UserController.resolvePageSize(null, 10L)).isEqualTo(10L);
        assertThat(UserController.resolvePageSize(null, null)).isEqualTo(20L);
    }

    @Test
    @DisplayName("GET /users：解析后的条数原样传给服务层（size 优先）")
    void pagePassesResolvedSizeToService() {
        controller.page("赵", 135L, true, "active", 12L, 2L, 50L, 10L);

        verify(userService).page("赵", 135L, true, "active", 12L, 2L, 50L);
    }

    @Test
    @DisplayName("GET /users：只给 pageSize 时按别名取数")
    void pageUsesLegacyAliasWhenSizeAbsent() {
        controller.page(null, null, true, null, null, 1L, null, 7L);

        verify(userService).page(null, null, true, null, null, 1L, 7L);
    }

    @Test
    @DisplayName("GET /users/{id}/in-flight-check：委托服务层，出参含待办数/在途数/明细")
    void inFlightCheckDelegates() {
        when(userService.inFlightCheck(7L)).thenReturn(new InFlightDtos.UserInFlightCheckView(
                2, 1, List.of(new InFlightDtos.InFlightItemView(9001L, "OA-2026-100003", "资金审批单",
                        "张三", "直属部门负责人", "直属部门负责人", "pending"))));

        InFlightDtos.UserInFlightCheckView view = controller.inFlightCheck(7L).getData();

        assertThat(view.pendingTaskCount()).isEqualTo(2);
        assertThat(view.inFlightInstanceCount()).isEqualTo(1);
        assertThat(view.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.instanceId()).isEqualTo(9001L);
                    assertThat(item.bizNo()).isEqualTo("OA-2026-100003");
                    assertThat(item.formType()).isEqualTo("资金审批单");
                    assertThat(item.currentNodeName()).isEqualTo("直属部门负责人");
                });
        verify(userService).inFlightCheck(7L);
    }

    @Test
    @DisplayName("PUT /users/{id}/positions/{positionId}：委托 PositionService#update（postName/isPrimary）")
    void updatePositionDelegates() {
        PositionDtos.PositionUpdateRequest request = new PositionDtos.PositionUpdateRequest("部门经理", null, true, null);
        controller.updatePosition(7L, 2L, request);

        verify(positionService).update(7L, 2L, request);
    }

    @Test
    @DisplayName("PUT /users/{id}：status 原样透传给服务层（active⇄disabled 的校验在服务层）")
    void updatePassesStatusThrough() {
        UserDtos.UserUpdateRequest request = new UserDtos.UserUpdateRequest(
                "李乙", "A0007", "13800000007", "liyi@example.com", null, null, "专员", null, "disabled", null, null);
        controller.update(7L, request);

        // AC-52：控制器把已解析的 reason/force 一并下传（未声明 force 时为 null，服务层按「默认阻断」处理）
        verify(userService).update(7L, request, null, null);
    }

    @Test
    @DisplayName("PUT /users/{id}：force=true 且原因为空 → 400，且不触达服务层（准入校验在控制器）")
    void updateWithForceAndBlankReasonIsRejectedBeforeService() {
        UserDtos.UserUpdateRequest request = new UserDtos.UserUpdateRequest(
                "李乙", "A0007", "13800000007", "liyi@example.com", null, null, "专员", null, "disabled", "  ", true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.update(7L, request))
                .isInstanceOf(com.oa.common.error.BizException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((com.oa.common.error.BizException) ex).getErrorCode().getHttpStatus()).isEqualTo(400));

        verify(userService, org.mockito.Mockito.never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PUT /users/{id}：force=true + 管理员 + 原因齐全 → 原样下传 force/reason（服务层据此放行）")
    void updatePassesForceAndReasonThrough() {
        com.oa.common.scope.DataScopeContext.set(com.oa.common.scope.DataScopeContext.builder()
                .principal(com.oa.common.security.CurrentUser.of(40L, "admin01", "系统管理员", "A0040", 1L, 1L,
                        java.util.Set.of("admin"), java.util.Set.of(), false))
                .build());
        try {
            UserDtos.UserUpdateRequest request = new UserDtos.UserUpdateRequest(
                    "李乙", "A0007", "13800000007", "liyi@example.com", null, null, "专员", null, "disabled",
                    "紧急停用", true);
            controller.update(7L, request);

            verify(userService).update(7L, request, true, "紧急停用");
        } finally {
            com.oa.common.scope.DataScopeContext.clear();
        }
    }

    @Test
    @DisplayName("GET /users/export：服务层返回的 CSV 带 BOM 与九列表头（下载文件名 user.csv）")
    void exportReturnsCsvAttachment() {
        when(userService.exportCsv(any(), any(), any(), any(), any()))
                .thenReturn("\uFEFFaccount,employee_no,name,phone,email,company_path,dept_path,status,remark\r\n");

        var response = controller.export(null, null, true, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("user.csv");
        assertThat(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                .startsWith("\uFEFFaccount,employee_no,name,");
        verify(userService).exportCsv(null, null, true, null, null);
    }

    @Test
    @DisplayName("GET /users/{id}/pending-tasks、positions、leader-of 的既有路径不变（向后兼容）")
    void legacyEndpointsStillDelegate() {
        controller.pendingTasks(7L);
        controller.positions(7L);
        controller.leaderOf(7L);

        verify(userService).pendingTasks(7L);
        verify(positionService).positions(7L);
        verify(leaderService).leaderOf(7L);
    }

    @Test
    @DisplayName("影响清单出参形状：桩/无影响时 items 为空数组（不是 null），字段名与前端约定逐字一致")
    void inFlightCheckJsonShape() throws Exception {
        // 生产口径：non_null（空字段省略）+ Long→字符串（见 JacksonConfig）
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json()
                .serializationInclusion(JsonInclude.Include.NON_NULL);
        new JacksonConfig().oaNumberAsStringCustomizer(new OaProperties()).customize(builder);
        ObjectMapper mapper = builder.build();

        String empty = mapper.writeValueAsString(new InFlightDtos.UserInFlightCheckView(0, 0, List.of()));
        assertThat(empty).isEqualTo("{\"pendingTaskCount\":0,\"inFlightInstanceCount\":0,\"items\":[]}");

        // 人员口径一行：七个字段齐全（instanceId 按字符串下发）
        String userItem = mapper.writeValueAsString(new InFlightDtos.InFlightItemView(9001L, "OA-2026-100003",
                "资金审批单", "张三", "直属部门负责人", "分公司分管领导", "pending"));
        assertThat(userItem).isEqualTo("{\"instanceId\":\"9001\",\"bizNo\":\"OA-2026-100003\","
                + "\"formType\":\"资金审批单\",\"initiatorName\":\"张三\",\"nodeName\":\"直属部门负责人\","
                + "\"currentNodeName\":\"分公司分管领导\",\"status\":\"pending\"}");

        // 组织口径一行：只出现 bizNo/formType/initiatorName/nodeName 四项（其余字段为 null 自动省略）
        String orgItem = mapper.writeValueAsString(new InFlightDtos.InFlightItemView(null, "ZJ-2026-000118",
                "资金审批单", "张三", "直属部门负责人", null, null));
        assertThat(orgItem).isEqualTo("{\"bizNo\":\"ZJ-2026-000118\",\"formType\":\"资金审批单\","
                + "\"initiatorName\":\"张三\",\"nodeName\":\"直属部门负责人\"}");
    }
}
