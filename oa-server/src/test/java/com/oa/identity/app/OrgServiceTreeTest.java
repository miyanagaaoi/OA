package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.config.OaProperties;
import com.oa.common.scope.DataScopeContext;
import com.oa.identity.api.dto.OrgDtos;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.infra.SysOrgLeaderMapper;
import com.oa.identity.infra.SysOrgMapper;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.SysUserPositionMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 组织树补缺单测（施工要求第 9/10 条）：
 * <ol>
 *   <li>{@code hasPrimaryLeader}（AC-11「未设正职」）来自 {@code sys_org_leader} 的**一次批量查询**，
 *       整棵树只查一次（杜绝 N+1）；</li>
 *   <li>{@code /orgs/selector?keyword=} 的服务端过滤**保留树形**：命中节点 + 其祖先链，
 *       父链不断（否则前端只能拿到孤儿节点）。</li>
 * </ol>
 */
class OrgServiceTreeTest {

    private SysOrgMapper orgMapper;
    private SysUserPositionMapper positionMapper;
    private SysOrgLeaderMapper leaderMapper;
    private SysUserMapper userMapper;
    private OrgService service;

    @BeforeEach
    void setUp() {
        orgMapper = mock(SysOrgMapper.class);
        positionMapper = mock(SysUserPositionMapper.class);
        leaderMapper = mock(SysOrgLeaderMapper.class);
        userMapper = mock(SysUserMapper.class);
        service = new OrgService(orgMapper, positionMapper, leaderMapper, userMapper,
                mock(InFlightChecker.class), new OaProperties());
        // 系统口径：全部节点可见（本测试只验证聚合与过滤逻辑，不验证数据域裁剪）
        DataScopeContext.set(DataScopeContext.system());
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

    /** 集团 → 公司A → 部门1（三级，与 import-spec 的 path 口径一致）。 */
    private static List<SysOrg> sampleTree() {
        return List.of(
                org(1L, null, "group", "集团", "/1/", 1),
                org(12L, 1L, "company", "公司A", "/1/12/", 2),
                org(135L, 12L, "dept", "部门1", "/1/12/135/", 3));
    }

    @Test
    @DisplayName("hasPrimaryLeader：一次批量查询聚合，树节点直接标示「未设正职」")
    void hasPrimaryLeaderUsesSingleBatchQuery() {
        when(orgMapper.selectAll(true)).thenReturn(sampleTree());
        // 只有公司A 设了正职（业务线分管领导不算正职，SQL 已按 category IS NULL 过滤）
        when(leaderMapper.selectPrimaryOrgIds()).thenReturn(List.of(12L));

        List<OrgDtos.OrgView> tree = service.tree(null, true);

        assertThat(tree).hasSize(1);
        OrgDtos.OrgView group = tree.get(0);
        assertThat(group.name()).isEqualTo("集团");
        assertThat(group.hasPrimaryLeader()).isFalse();

        OrgDtos.OrgView company = group.children().get(0);
        assertThat(company.name()).isEqualTo("公司A");
        assertThat(company.hasPrimaryLeader()).isTrue();

        OrgDtos.OrgView dept = company.children().get(0);
        assertThat(dept.name()).isEqualTo("部门1");
        assertThat(dept.hasPrimaryLeader()).isFalse();

        // 关键：整棵树只做了一次批量查询（不因节点数增长而增长）
        verify(leaderMapper, times(1)).selectPrimaryOrgIds();
    }

    @Test
    @DisplayName("selector?keyword=：服务端过滤命中节点，并保留其祖先链（结构仍是树）")
    void selectorKeywordKeepsAncestors() {
        when(orgMapper.selectAll(false)).thenReturn(sampleTree());

        List<OrgDtos.OrgOption> options = service.selector(null, false, "部门1");

        assertThat(options).hasSize(1);
        OrgDtos.OrgOption group = options.get(0);
        assertThat(group.label()).isEqualTo("集团");
        assertThat(group.children()).hasSize(1);
        OrgDtos.OrgOption company = group.children().get(0);
        assertThat(company.label()).isEqualTo("公司A");
        assertThat(company.children()).hasSize(1);
        assertThat(company.children().get(0).label()).isEqualTo("部门1");
        assertThat(company.children().get(0).children()).isNull();
    }

    @Test
    @DisplayName("selector 无 keyword：返回完整可见树（行为与旧调用一致）")
    void selectorWithoutKeywordReturnsFullTree() {
        when(orgMapper.selectAll(false)).thenReturn(sampleTree());

        List<OrgDtos.OrgOption> options = service.selector(null, false, null);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).children()).hasSize(1);
        assertThat(options.get(0).children().get(0).children()).hasSize(1);
    }

    @Test
    @DisplayName("selector?keyword=未命中：返回空列表而不是整棵树")
    void selectorWithUnmatchedKeywordIsEmpty() {
        when(orgMapper.selectAll(false)).thenReturn(sampleTree());

        assertThat(service.selector(null, false, "不存在的部门")).isEmpty();
    }
}
