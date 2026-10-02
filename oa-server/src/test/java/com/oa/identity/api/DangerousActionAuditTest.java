package com.oa.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.api.ApiResponse;
import com.oa.common.audit.AuditAspect;
import com.oa.common.audit.AuditLogWriter;
import com.oa.common.audit.Audited;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.identity.api.dto.OrgDtos;
import com.oa.identity.api.dto.UserDtos;
import com.oa.identity.app.OrgLeaderService;
import com.oa.identity.app.OrgService;
import com.oa.identity.app.PositionService;
import com.oa.identity.app.UserService;
import java.lang.reflect.Method;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * 危险操作「接收原因 + 留痕」单测（施工要求第 7 条 / AC-52）。
 *
 * <p>验证两件事：
 * <ol>
 *   <li><b>准入</b>：直接调用控制器方法（真实接线）——
 *       {@code force=true} + 空原因 → 400，且**不会**触达业务服务；</li>
 *   <li><b>留痕</b>：{@code AuditAspect} 会把 {@code reason}/{@code force} 写进
 *       {@code sys_log} 的入参 JSON（{@code recordBefore/recordArgs=true}）——
 *       这里用真实的切面 + 捕获 {@link AuditLogWriter.AuditRecord} 来断言，
 *       而不是只看注解有没有写。</li>
 * </ol>
 */
class DangerousActionAuditTest {

    private static CurrentUser admin() {
        return CurrentUser.of(40L, "admin01", "系统管理员", "A0040", 1L, 1L, Set.of("admin"), Set.of(), false);
    }

    private static ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        builder.featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return builder.build();
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("停用组织：force=true 且原因为空 → 400，且不调用 orgService.disable")
    void disableWithForceAndBlankReasonIsRejectedBeforeService() {
        OrgService orgService = mock(OrgService.class);
        OrgController controller = new OrgController(orgService, mock(OrgLeaderService.class));

        assertThatThrownBy(() -> controller.disable(9L, new OrgDtos.StateChangeRequest("  ", true)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(400);
                })
                .hasMessageContaining("必须填写原因");

        verify(orgService, never()).disable(any());
    }

    @Test
    @DisplayName("停用组织：force=true 但非系统管理员 → 403")
    void disableWithForceRequiresAdmin() {
        DataScopeContext.set(DataScopeContext.builder()
                .principal(CurrentUser.of(7L, "u07", "赵庚", "A0007", 1351L, 12L, Set.of("employee"), Set.of(), false))
                .build());
        OrgController controller = new OrgController(mock(OrgService.class), mock(OrgLeaderService.class));

        assertThatThrownBy(() -> controller.disable(9L, new OrgDtos.StateChangeRequest("组织重组", true)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("离职：force=true 且原因为空 → 400（控制器接线真实生效）")
    void resignWithForceAndBlankReasonIsRejected() {
        UserController controller = new UserController(mock(UserService.class), mock(PositionService.class),
                mock(OrgLeaderService.class));

        assertThatThrownBy(() -> controller.resign(7L, new UserDtos.ResignRequest(null, true)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID))
                .hasMessageContaining("AC-52");
    }

    @Test
    @DisplayName("审计 JSON 含 reason/force：@Audited 的入参序列化即留痕（AC-52）")
    void auditJsonCarriesReasonAndForce() throws Throwable {
        DataScopeContext.set(DataScopeContext.builder().principal(admin()).build());

        AuditLogWriter writer = mock(AuditLogWriter.class);
        AuditAspect aspect = new AuditAspect(writer, objectMapper());

        OrgDtos.StateChangeRequest request = new OrgDtos.StateChangeRequest("组织重组：撤销分公司B", true);
        Method method = OrgController.class.getMethod("disable", Long.class, OrgDtos.StateChangeRequest.class);
        Audited audited = method.getAnnotation(Audited.class);
        assertThat(audited).as("disable 必须标 @Audited").isNotNull();

        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(new String[]{"id", "request"});
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{9L, request});
        when(joinPoint.proceed()).thenReturn(ApiResponse.success(null));

        aspect.around(joinPoint, audited);

        ArgumentCaptor<AuditLogWriter.AuditRecord> captor = ArgumentCaptor.forClass(AuditLogWriter.AuditRecord.class);
        verify(writer).append(captor.capture());
        AuditLogWriter.AuditRecord record = captor.getValue();

        assertThat(record.action()).isEqualTo("disable");
        assertThat(record.targetType()).isEqualTo("org");
        assertThat(record.targetId()).isEqualTo(9L);
        assertThat(record.beforeJson())
                .as("危险操作的原因与强制标记必须进 sys_log 的入参 JSON")
                .contains("\"reason\":\"组织重组：撤销分公司B\"")
                .contains("\"force\":true");
    }

    @Test
    @DisplayName("被拒绝的强制尝试同样留痕：切面在异常路径也写入入参 JSON")
    void rejectedAttemptIsStillAudited() throws Throwable {
        DataScopeContext.set(DataScopeContext.builder().principal(admin()).build());

        AuditLogWriter writer = mock(AuditLogWriter.class);
        AuditAspect aspect = new AuditAspect(writer, objectMapper());

        OrgDtos.StateChangeRequest request = new OrgDtos.StateChangeRequest("越权尝试", true);
        Method method = OrgController.class.getMethod("disable", Long.class, OrgDtos.StateChangeRequest.class);
        Audited audited = method.getAnnotation(Audited.class);

        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(new String[]{"id", "request"});
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{9L, request});
        when(joinPoint.proceed()).thenThrow(new BizException(ErrorCode.CONFLICT, "仍有在途单据"));

        assertThatThrownBy(() -> aspect.around(joinPoint, audited))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仍有在途单据");

        ArgumentCaptor<AuditLogWriter.AuditRecord> captor = ArgumentCaptor.forClass(AuditLogWriter.AuditRecord.class);
        verify(writer).append(captor.capture());
        assertThat(captor.getValue().beforeJson()).contains("\"reason\":\"越权尝试\"");
        assertThat(captor.getValue().afterJson()).contains("BizException");
    }
}
