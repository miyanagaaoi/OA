package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.config.OaProperties;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.identity.api.dto.OrgDtos;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.infra.SysOrgLeaderMapper;
import com.oa.identity.infra.SysOrgMapper;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.SysUserPositionMapper;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code POST /api/v1/identity/orgs}（{@link OrgService#create}）的补缺单测。
 *
 * <p><b>回归对象</b>：{@code sys_org.path} 是 {@code NOT NULL} 且有唯一键 {@code uk_sys_org_path}，
 * 而真实 path 依赖自增 id。create 的注释写着「先以临时唯一 path 落库，拿到 id 后回填真实 path」，
 * 但历史上代码**从未 setPath** → 全新库上建组织必报
 * {@code Column 'path' cannot be null}（500）。
 *
 * <p>本测试锁死三件事：
 * <ol>
 *   <li>{@code insertOrg} 时 path <b>非 null</b> 且两次调用<b>互不相同</b>（并发同名不撞唯一键）；</li>
 *   <li>回填的真实 path / depth 复用 {@link OrgHierarchy} 口径（{@code /父/子/} + 按类型推导 depth）；</li>
 *   <li>出参给出的是真实 path，而不是临时 path。</li>
 * </ol>
 */
class OrgServiceCreateTest {

    /** 集团 id（自增起点）。 */
    private static final long GROUP_ID = 1L;

    /** 公司 id。 */
    private static final long COMPANY_ID = 12L;

    /** 新建部门拿到的自增 id。 */
    private static final long DEPT_ID = 135L;

    private static final long OPERATOR_ID = 10086L;

    private SysOrgMapper orgMapper;
    private SysOrgLeaderMapper leaderMapper;
    private OrgService service;

    @BeforeEach
    void setUp() {
        orgMapper = mock(SysOrgMapper.class);
        leaderMapper = mock(SysOrgLeaderMapper.class);
        service = new OrgService(orgMapper, mock(SysUserPositionMapper.class), leaderMapper,
                mock(SysUserMapper.class), mock(InFlightChecker.class), new OaProperties());
        CurrentUser principal = CurrentUser.of(OPERATOR_ID, "admin", "系统管理员", "10086", GROUP_ID, null,
                Set.of("admin"), EnumSet.of(DataScopeType.GROUP_ALL), false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .scopes(EnumSet.of(DataScopeType.GROUP_ALL))
                .system(true)
                .build());
        when(leaderMapper.selectPrimaryOrgIds()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private static SysOrg org(long id, Long parentId, String type, String name, String path, int depth) {
        SysOrg node = new SysOrg();
        node.setId(id);
        node.setParentId(parentId);
        node.setOrgType(type);
        node.setName(name);
        node.setPath(path);
        node.setDepth(depth);
        node.setSortNo(0);
        node.setStatus("active");
        return node;
    }

    private static SysOrg company() {
        return org(COMPANY_ID, GROUP_ID, "company", "公司A", "/1/12/", 2);
    }

    private static SysOrg createdDept() {
        return org(DEPT_ID, COMPANY_ID, "dept", "部门1", "/1/12/135/", 3);
    }

    /** 集团(1) → 公司A(12) → 部门1(135)：新增落库后全量查询里已含该部门。 */
    private static List<SysOrg> fullTree() {
        return List.of(org(GROUP_ID, null, "group", "集团", "/1/", 1), company(), createdDept());
    }

    private void stubCreate() {
        when(orgMapper.insertOrg(any(SysOrg.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysOrg.class).setId(DEPT_ID);
            return 1;
        });
        when(orgMapper.selectAll(true)).thenReturn(fullTree());
        when(orgMapper.selectById(COMPANY_ID)).thenReturn(company());
        when(orgMapper.selectById(DEPT_ID)).thenReturn(createdDept());
    }

    private OrgDtos.OrgCreateRequest request() {
        return new OrgDtos.OrgCreateRequest("部门1", "dept", COMPANY_ID, 0, "active", null);
    }

    @Test
    @DisplayName("create：insertOrg 必须带非空临时 path（path 依赖自增 id，NOT NULL 列不得留空）")
    void createAlwaysSetsTemporaryPathBeforeInsert() {
        stubCreate();

        service.create(request());

        ArgumentCaptor<SysOrg> inserted = ArgumentCaptor.forClass(SysOrg.class);
        verify(orgMapper).insertOrg(inserted.capture());
        String tempPath = inserted.getValue().getPath();
        assertThat(tempPath).as("sys_org.path 为 NOT NULL，插入前必须给临时 path").isNotNull();
        assertThat(tempPath).startsWith("/pending-").endsWith("/");
    }

    @Test
    @DisplayName("create：回填真实 path/depth（/父/子/ + 按类型推导），出参不留临时 path")
    void createBackfillsRealPathAndDepth() {
        stubCreate();

        OrgDtos.OrgView view = service.create(request());

        verify(orgMapper).updatePathAndDepth(eq(DEPT_ID), eq("/1/12/135/"), eq(3), eq(OPERATOR_ID));
        assertThat(view.path()).isEqualTo("/1/12/135/");
        assertThat(view.depth()).isEqualTo(3);
    }

    @Test
    @DisplayName("create：并发同名（不同父）临时 path 全局唯一，不撞 uk_sys_org_path")
    void createUsesUniqueTemporaryPathPerCall() {
        stubCreate();

        service.create(request());
        service.create(request());

        ArgumentCaptor<SysOrg> inserted = ArgumentCaptor.forClass(SysOrg.class);
        verify(orgMapper, times(2)).insertOrg(inserted.capture());
        List<String> paths = inserted.getAllValues().stream().map(SysOrg::getPath).toList();
        assertThat(paths).doesNotContainNull();
        assertThat(paths.get(0)).isNotEqualTo(paths.get(1));
    }
}
