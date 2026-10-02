package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「强制继续」准入单测（施工要求第 7 条 / AC-52）：
 * {@code force=true} 时**原因必填**（否则 400）、**必须是系统管理员**（否则 403）；
 * {@code force} 未传或为 {@code false} 时一律放行（向后兼容：旧调用不带 body 也不报错）。
 */
class ForceReasonPolicyTest {

    private static CurrentUser principal(String... roles) {
        return CurrentUser.of(40L, "u40", "系统管理员", "A0040", 1L, 1L, Set.of(roles), Set.of(), false);
    }

    private static void authenticateAs(CurrentUser user) {
        DataScopeContext.set(DataScopeContext.builder().principal(user).build());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("force=true 且原因为空 → 400（PARAM_INVALID），文案指向 AC-52")
    void forceWithoutReasonIsRejectedAsBadRequest() {
        assertThatThrownBy(() -> ForceReasonPolicy.assertAllowed(true, null, principal("admin"), "停用组织节点"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(400);
                })
                .hasMessageContaining("必须填写原因")
                .hasMessageContaining("AC-52");

        // 纯空白同样视为未填
        assertThatThrownBy(() -> ForceReasonPolicy.assertAllowed(true, "   ", principal("admin"), "停用组织节点"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("必须填写原因");
    }

    @Test
    @DisplayName("force=true 且非系统管理员 → 403（FORBIDDEN）")
    void forceWithoutAdminRoleIsForbidden() {
        assertThatThrownBy(() -> ForceReasonPolicy.assertAllowed(true, "组织重组", principal("employee"), "停用组织节点"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(403);
                })
                .hasMessageContaining("仅系统管理员");

        // 未认证（无上下文 → 无主体）按无权限处理
        assertThatThrownBy(() -> ForceReasonPolicy.assertAllowed(true, "组织重组", "停用组织节点"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("force=true + 原因 + 系统管理员 → 放行")
    void forceWithReasonAndAdminPasses() {
        assertThatCode(() -> ForceReasonPolicy.assertAllowed(true, "组织重组（AC-52 强制继续）",
                principal("admin"), "停用组织节点")).doesNotThrowAnyException();
        assertThat(ForceReasonPolicy.isForce(true)).isTrue();
    }

    @Test
    @DisplayName("未传 force / force=false → 不校验原因与角色（旧调用不带 body 不报错）")
    void forceAbsentKeepsBackwardCompatibility() {
        assertThatCode(() -> ForceReasonPolicy.assertAllowed(null, null, principal("employee"), "停用组织节点"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ForceReasonPolicy.assertAllowed(false, null, null, "停用组织节点"))
                .doesNotThrowAnyException();
        assertThat(ForceReasonPolicy.isForce(null)).isFalse();
        assertThat(ForceReasonPolicy.isForce(false)).isFalse();
    }

    @Test
    @DisplayName("从请求上下文取操作人：管理员放行、普通员工 403")
    void contextOverloadReadsCurrentPrincipal() {
        authenticateAs(principal("admin"));
        assertThatCode(() -> ForceReasonPolicy.assertAllowed(true, "原因", "停用组织节点"))
                .doesNotThrowAnyException();

        authenticateAs(principal("employee"));
        assertThatThrownBy(() -> ForceReasonPolicy.assertAllowed(true, "原因", "停用组织节点"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
