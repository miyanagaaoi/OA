package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.authz.visibility.PhoneVisibilityService;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.common.security.PasswordService;
import com.oa.common.security.SessionStore;
import com.oa.identity.api.dto.OrgDtos;
import com.oa.identity.api.dto.UserDtos;
import com.oa.identity.app.InFlightChecker.InFlightSummary;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysUser;
import com.oa.identity.domain.SysUserSession;
import com.oa.identity.infra.SysOrgLeaderMapper;
import com.oa.identity.infra.SysOrgMapper;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.SysUserPositionMapper;
import com.oa.platform.security.crypto.PhoneCryptoService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「管理员强制继续可以放行在途/待办阻断」的**服务层**单测（业务裁定落地校验，纯逻辑 + Mockito，不依赖 DB/Redis/Spring）。
 *
 * <p>三条口径：
 * <ol>
 *   <li>{@code force=true}（已由控制器 {@link ForceReasonPolicy} 准入）→ **不再抛 409**，请求继续执行；</li>
 *   <li>{@code force=null/false} → 行为与改造前**完全一致**（命中即 409，状态不变）；</li>
 *   <li>服务层不读取请求体里的 {@code force}：只认**显式参数**（避免绕过控制器准入）。</li>
 * </ol>
 */
class InFlightForceOverrideServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ORG_ID = 12L;

    /** 恒定命中：名下 2 条待办 / 子树 3 张在途单据（AC-12 / AC-11 的取样口径）。 */
    private static final InFlightSummary USER_SUMMARY =
            new InFlightSummary(0, 2, List.of("OA-2026-100003", "OA-2026-100004"));
    private static final InFlightSummary ORG_SUMMARY =
            new InFlightSummary(3, 0, List.of("ZJ-2026-000118", "ZJ-2026-000123"));

    private SysUserMapper userMapper;
    private SysOrgMapper orgMapper;
    private SysUserPositionMapper positionMapper;
    private InFlightChecker checker;
    private SessionStore sessionStore;
    private OrgLeaderService leaderService;
    private OaProperties properties;

    private UserService userService;
    private OrgService orgService;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        orgMapper = mock(SysOrgMapper.class);
        positionMapper = mock(SysUserPositionMapper.class);
        checker = mock(InFlightChecker.class);
        sessionStore = mock(SessionStore.class);
        leaderService = mock(OrgLeaderService.class);
        properties = new OaProperties();
        // 默认口径：命中即拒绝（oa.identity.block-on-inflight=true）
        assertThat(properties.getIdentity().isBlockOnInflight())
                .as("默认必须是硬阻断，force 才谈得上覆盖")
                .isTrue();

        PhoneCryptoService phoneCrypto = new PhoneCryptoService(TestPhoneKeys.cipher(), userMapper);
        userService = new UserService(userMapper, orgMapper, positionMapper, mock(OrgService.class),
                leaderService, checker, mock(PasswordService.class), sessionStore, properties,
                phoneCrypto, new PhoneVisibilityService(phoneCrypto));
        orgService = new OrgService(orgMapper, positionMapper, mock(SysOrgLeaderMapper.class), userMapper,
                checker, properties);

        DataScopeContext.set(DataScopeContext.builder()
                .system(true)
                .principal(CurrentUser.of(40L, "admin01", "系统管理员", "A0040", 1L, 1L,
                        Set.of("admin"), Set.of(), false))
                .build());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    // ------------------------------------------------------------------ 夹具

    private static SysUser user(String status) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setAccount("liyi0002");
        user.setName("李乙");
        user.setEmployeeNo("A0007");
        user.setStatus(status);
        return user;
    }

    private static SysOrg org() {
        SysOrg node = new SysOrg();
        node.setId(ORG_ID);
        node.setParentId(1L);
        node.setOrgType("company");
        node.setName("公司A");
        node.setPath("/1/12/");
        node.setDepth(2);
        node.setSortNo(0);
        node.setStatus("active");
        return node;
    }

    private void stubUser() {
        when(userMapper.selectUserById(USER_ID)).thenReturn(user("active"));
        when(checker.checkUser(USER_ID)).thenReturn(USER_SUMMARY);
        when(leaderService.leaderOf(USER_ID)).thenReturn(List.of());
        when(sessionStore.revokeAll(anyLong(), anyString())).thenReturn(1);
    }

    private void stubOrg() {
        SysOrg node = org();
        when(orgMapper.selectById(ORG_ID)).thenReturn(node);
        when(orgMapper.selectAll(true)).thenReturn(List.of(node));
        when(checker.checkOrgSubtree("/1/12/")).thenReturn(ORG_SUMMARY);
        when(checker.orgInFlightItems("/1/12/")).thenReturn(List.of());
    }

    private static UserDtos.UserUpdateRequest disableRequest(Boolean force, String reason) {
        return new UserDtos.UserUpdateRequest("李乙", "A0007", "13800000007", "liyi@example.com",
                null, null, "专员", null, "disabled", reason, force);
    }

    // ------------------------------------------------------------------ UserService#resign

    @Test
    @DisplayName("resign(force=true)：命中待办不再 409，请求继续执行（状态已改为离职 + 撤销会话）")
    void resignWithForceReleasesPendingTasks() {
        stubUser();

        UserDtos.ResignResult result = userService.resign(USER_ID,
                new UserDtos.ResignRequest("组织重组：撤销该公司", true), true, "组织重组：撤销该公司");

        assertThat(result.status()).isEqualTo("resigned");
        assertThat(result.pendingTasks()).isEqualTo(2);
        assertThat(result.message()).contains("强制继续").contains("2 条未处理待办");
        verify(userMapper).updateUserStatus(USER_ID, "resigned", 40L);
        verify(sessionStore).revokeAll(USER_ID, SysUserSession.REASON_DISABLED);
    }

    @Test
    @DisplayName("resign(force=false/null)：行为不变 —— 命中待办仍 409，且不触碰人员状态")
    void resignWithoutForceStillBlocks() {
        stubUser();

        assertThatThrownBy(() -> userService.resign(USER_ID,
                new UserDtos.ResignRequest("不算强制", false), false, "不算强制"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("OA-2026-100003");

        assertThatThrownBy(() -> userService.resign(USER_ID, new UserDtos.ResignRequest(null, null)))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> userService.resign(USER_ID,
                new UserDtos.ResignRequest("原因齐全但未授权", true), null, "原因齐全但未授权"))
                .as("服务层只认显式 force 参数：请求体里的 force 不构成授权（绕过控制器准入的路径必须仍阻断）")
                .isInstanceOf(BizException.class);

        verify(userMapper, never()).updateUserStatus(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("resign(force=true)：待办清零时照常完成（force 不改变无命中场景）")
    void resignWithForceWhenClear() {
        when(userMapper.selectUserById(USER_ID)).thenReturn(user("active"));
        when(checker.checkUser(USER_ID)).thenReturn(InFlightSummary.none());
        when(leaderService.leaderOf(USER_ID)).thenReturn(List.of());

        UserDtos.ResignResult result = userService.resign(USER_ID,
                new UserDtos.ResignRequest("强制", true), true, "强制");

        assertThat(result.pendingTasks()).isZero();
        assertThat(result.message()).startsWith("离职办理完成").doesNotContain("强制继续");
    }

    // ------------------------------------------------------------------ UserService#update（切停用）

    @Test
    @DisplayName("update(force=true)：切 disabled 命中待办不再 409，请求继续执行（状态已改为 disabled）")
    void updateToDisabledWithForceReleases() {
        // 首次读取是「在职」的人，服务层落库后重读为「停用」——与真实链路一致
        when(userMapper.selectUserById(USER_ID)).thenReturn(user("active"), user("disabled"));
        when(checker.checkUser(USER_ID)).thenReturn(USER_SUMMARY);
        when(checker.pendingTaskCounts(any())).thenReturn(java.util.Map.of(USER_ID, 2));

        UserDtos.UserView view = userService.update(USER_ID, disableRequest(true, "紧急停用"), true, "紧急停用");

        assertThat(view.status()).isEqualTo("disabled");
        verify(userMapper).updateUserStatus(USER_ID, "disabled", 40L);
    }

    @Test
    @DisplayName("update(force=false/null)：切 disabled 命中待办仍 409（默认阻断不变）")
    void updateToDisabledWithoutForceBlocks() {
        stubUser();

        assertThatThrownBy(() -> userService.update(USER_ID, disableRequest(false, "不强制"), false, "不强制"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("停用前必须清空名下待办");

        assertThatThrownBy(() -> userService.update(USER_ID, disableRequest(null, null)))
                .isInstanceOf(BizException.class);

        verify(userMapper, never()).updateUserStatus(anyLong(), anyString(), any());
    }

    // ------------------------------------------------------------------ OrgService#disable

    @Test
    @DisplayName("OrgService.disable(force=true)：命中在途不再 409，节点已停用且出参说明「强制放行」")
    void orgDisableWithForceReleasesInFlight() {
        stubOrg();

        OrgDtos.StateResult result = orgService.disable(ORG_ID, true, "分公司撤销");

        assertThat(result.status()).isEqualTo("disabled");
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning)
                .contains("强制继续").contains("3 张在途单据").contains("ZJ-2026-000118"));
        assertThat(result.impact().blocked()).isTrue();
        verify(orgMapper).updateStatus(ORG_ID, "disabled", 40L);
    }

    @Test
    @DisplayName("OrgService.disable(force=false/null)：命中在途仍 409，状态保持不变")
    void orgDisableWithoutForceBlocks() {
        stubOrg();

        assertThatThrownBy(() -> orgService.disable(ORG_ID, false, "不强制"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("组织停用前必须清空在途单据");
        assertThatThrownBy(() -> orgService.disable(ORG_ID))
                .isInstanceOf(BizException.class);

        verify(orgMapper, never()).updateStatus(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("block-on-inflight=false（仅告警）：force 与否都不阻断，force 不改变既有结论")
    void warnOnlyModeIgnoresForce() {
        properties.getIdentity().setBlockOnInflight(false);
        stubOrg();

        OrgDtos.StateResult result = orgService.disable(ORG_ID, true, "仅告警模式下的 force");

        assertThat(result.status()).isEqualTo("disabled");
        assertThat(result.impact().rejected()).isFalse();
        verify(orgMapper).updateStatus(ORG_ID, "disabled", 40L);
    }

    // ------------------------------------------------------------------ enable / move（无在途判定，仅贯通）

    @Test
    @DisplayName("OrgService.enable(force=true)：无在途判定 → 照常启用（参数贯通不改变语义）")
    void orgEnableWithForceKeepsBehaviour() {
        SysOrg disabled = org();
        disabled.setStatus("disabled");
        when(orgMapper.selectById(ORG_ID)).thenReturn(disabled);
        when(orgMapper.selectAll(true)).thenReturn(List.of(disabled));

        OrgDtos.StateResult result = orgService.enable(ORG_ID, true, "恢复公司A");

        assertThat(result.status()).isEqualTo("active");
        verify(orgMapper).updateStatus(ORG_ID, "active", 40L);
    }
}
