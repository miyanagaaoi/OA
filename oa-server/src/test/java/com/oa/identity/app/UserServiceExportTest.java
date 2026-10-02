package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.common.security.PasswordService;
import com.oa.common.security.SessionStore;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysUser;
import com.oa.identity.infra.SysOrgMapper;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.SysUserPositionMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 人员主数据导出单测（施工要求第 6 条 / import-spec §9.1 + §9.2）：
 * <ol>
 *   <li>列名与顺序逐字为 {@code account,employee_no,name,phone,email,company_path,dept_path,status,remark}；</li>
 *   <li>UTF-8 **带 BOM** + CRLF（否则校验器报 {@code E-ENC-001}，Excel 中文乱码）；</li>
 *   <li>{@code company_path}/{@code dept_path} 取**名称路径**（往返导入的业务键）；</li>
 *   <li>非系统管理员 → 403（{@code EXPORT_DENIED}）。</li>
 * </ol>
 */
class UserServiceExportTest {

    /** user.csv 的九列（import-spec §9.1，顺序不得变）。 */
    private static final String HEADER =
            "\uFEFFaccount,employee_no,name,phone,email,company_path,dept_path,status,remark";

    private SysUserMapper userMapper;
    private OrgService orgService;
    private UserService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        orgService = mock(OrgService.class);
        service = new UserService(userMapper, mock(SysOrgMapper.class), mock(SysUserPositionMapper.class),
                orgService, mock(OrgLeaderService.class), mock(InFlightChecker.class),
                mock(PasswordService.class), mock(SessionStore.class), new OaProperties());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private static void authenticateAs(String... roles) {
        CurrentUser principal = CurrentUser.of(40L, "admin01", "系统管理员", "A0040", 1L, 1L,
                Set.of(roles), Set.of(), false);
        DataScopeContext.set(DataScopeContext.builder().principal(principal).build());
    }

    private static SysUser user(long id, String account, String name, String status, Long orgId, Long companyId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setAccount(account);
        user.setEmployeeNo("A000" + id);
        user.setName(name);
        user.setPhone("1380000000" + id);
        user.setEmail(account + "@example.com");
        user.setOrgId(orgId);
        user.setCompanyId(companyId);
        user.setStatus(status);
        user.setRemark("骨干");
        return user;
    }

    @Test
    @DisplayName("导出九列 + UTF-8 BOM + CRLF，路径列取名称路径（往返导入的业务键）")
    void exportCsvMatchesUserTemplate() {
        authenticateAs("admin");
        when(userMapper.selectForExport(null, null, null, null, null)).thenReturn(List.of(
                user(4L, "zhaosi0001", "赵四", "active", 135L, 12L),
                user(5L, "wangwu0003", "王五", "disabled", null, 12L)));
        when(orgService.businessPathIndex()).thenReturn(Map.of(
                12L, "集团/公司A",
                135L, "集团/公司A/部门1"));

        String csv = service.exportCsv(null, null, true, null, null);
        String[] lines = csv.split("\r\n", -1);

        assertThat(csv).startsWith("\uFEFF");
        assertThat(lines[0]).isEqualTo(HEADER);
        assertThat(lines[1]).isEqualTo("zhaosi0001,A0004,赵四,13800000004,zhaosi0001@example.com,"
                + "集团/公司A,集团/公司A/部门1,在职,骨干");
        // 停用人员按 label 导出（import-spec T-05：停用不经导入，故该行不参与往返校验）
        assertThat(lines[2]).isEqualTo("wangwu0003,A0005,王五,13800000005,wangwu0003@example.com,"
                + "集团/公司A,,停用,骨干");
        assertThat(lines[3]).isEmpty();
        assertThat(lines).hasSize(4);
    }

    @Test
    @DisplayName("导出筛选参数原样下推（状态 code 归一化 + 组织子树前缀）")
    void exportCsvPushesFiltersDown() {
        authenticateAs("admin");
        when(orgService.requireVisibleOrg(135L)).thenReturn(orgNode(135L, "/1/12/135/"));
        when(userMapper.selectForExport("赵", "active", null, "/1/12/135/%", 12L)).thenReturn(List.of());
        when(orgService.businessPathIndex()).thenReturn(Map.of());

        String csv = service.exportCsv("赵", 135L, true, "在职", 12L);

        assertThat(csv.split("\r\n", -1)[0]).isEqualTo(HEADER);
        assertThat(csv).doesNotContain("集团");
    }

    @Test
    @DisplayName("非系统管理员 → 403（EXPORT_DENIED），且不查库")
    void exportRequiresSystemAdmin() {
        authenticateAs("employee");

        assertThatThrownBy(() -> service.exportCsv(null, null, true, null, null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.EXPORT_DENIED);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(403);
                })
                .hasMessageContaining("仅系统管理员");

        verifyNoInteractions(userMapper);
    }

    private static SysOrg orgNode(long id, String path) {
        SysOrg node = new SysOrg();
        node.setId(id);
        node.setPath(path);
        node.setOrgType("dept");
        node.setName("部门1");
        return node;
    }
}
