package com.oa.admin.bulk;

import com.oa.authz.domain.SysRole;
import com.oa.authz.infra.SysRoleMapper;
import com.oa.authz.infra.SysUserRoleMapper;
import com.oa.common.scope.DataScopeContext;
import com.oa.identity.app.OrgHierarchy;
import com.oa.identity.app.OrgService;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysOrgLeader;
import com.oa.identity.domain.SysUser;
import com.oa.identity.domain.SysUserPosition;
import com.oa.identity.infra.SysOrgLeaderMapper;
import com.oa.identity.infra.SysOrgMapper;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.SysUserPositionMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 导入用的**全量只读索引**（每次导入构建一次，避免逐行查库造成 N+1）。
 *
 * <p>调用约定：由 {@code BulkImportService} 在 {@link DataScopeContext#system()} 口径下构建与使用，
 * 因此索引是「全库真相」；授权由 {@link ImportScopeGuard} 逐行显式判定
 * （若索引被数据域裁剪，域外行会退化成"不存在"，错误信息会误导读数人）。
 */
@Service
public class ImportLookup {

    private final SysOrgMapper orgMapper;

    private final SysUserMapper userMapper;

    private final SysOrgLeaderMapper leaderMapper;

    private final SysUserPositionMapper positionMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final SysRoleMapper roleMapper;

    public ImportLookup(SysOrgMapper orgMapper, SysUserMapper userMapper, SysOrgLeaderMapper leaderMapper,
                        SysUserPositionMapper positionMapper, SysUserRoleMapper userRoleMapper,
                        SysRoleMapper roleMapper) {
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
        this.leaderMapper = leaderMapper;
        this.positionMapper = positionMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    /** 一次导入会话的索引快照。 */
    public Snapshot snapshot() {
        List<SysOrg> orgs = orgMapper.selectAll(true);
        Map<Long, SysOrg> byId = new LinkedHashMap<>();
        for (SysOrg org : orgs) {
            byId.put(org.getId(), org);
        }
        Map<Long, String> namePathIndex = OrgService.businessPathIndex(orgs);
        Map<String, SysOrg> byBusinessPath = new LinkedHashMap<>();
        Map<String, SysOrg> byRealPath = new LinkedHashMap<>();
        for (SysOrg org : orgs) {
            String businessPath = namePathIndex.get(org.getId());
            if (businessPath != null) {
                byBusinessPath.put(businessPath, org);
            }
            byRealPath.put(OrgHierarchy.normalize(org.getPath()), org);
        }
        return new Snapshot(byId, byBusinessPath, byRealPath, userMapper, leaderMapper, positionMapper,
                userRoleMapper, roleMapper);
    }

    /** 只读索引快照（导入期间不变；写入后需重新取快照）。 */
    public static final class Snapshot {

        private final Map<Long, SysOrg> orgsById;

        private final Map<String, SysOrg> orgsByBusinessPath;

        private final Map<String, SysOrg> orgsByRealPath;

        private final SysUserMapper userMapper;

        private final SysOrgLeaderMapper leaderMapper;

        private final SysUserPositionMapper positionMapper;

        private final SysUserRoleMapper userRoleMapper;

        private final SysRoleMapper roleMapper;

        private final Map<String, SysUser> userCache = new HashMap<>();

        private final Map<String, SysRole> roleCache = new HashMap<>();

        Snapshot(Map<Long, SysOrg> orgsById, Map<String, SysOrg> orgsByBusinessPath,
                 Map<String, SysOrg> orgsByRealPath, SysUserMapper userMapper, SysOrgLeaderMapper leaderMapper,
                 SysUserPositionMapper positionMapper, SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper) {
            this.orgsById = orgsById;
            this.orgsByBusinessPath = orgsByBusinessPath;
            this.orgsByRealPath = orgsByRealPath;
            this.userMapper = userMapper;
            this.leaderMapper = leaderMapper;
            this.positionMapper = positionMapper;
            this.userRoleMapper = userRoleMapper;
            this.roleMapper = roleMapper;
        }

        public Map<Long, SysOrg> orgsById() {
            return orgsById;
        }

        /** 业务键 → 组织节点（{@code org_path}，名称路径）。 */
        public SysOrg orgByBusinessPath(String businessPath) {
            return businessPath == null ? null : orgsByBusinessPath.get(businessPath.trim());
        }

        /** 真实路径（{@code /1/12/}）→ 组织节点。 */
        public SysOrg orgByRealPath(String realPath) {
            return realPath == null ? null : orgsByRealPath.get(OrgHierarchy.normalize(realPath));
        }

        /** 账号 → 用户（全局口径；含离职/停用行）。 */
        public SysUser userByAccount(String account) {
            if (account == null || account.isBlank()) {
                return null;
            }
            String key = account.trim();
            if (userCache.containsKey(key)) {
                return userCache.get(key);
            }
            SysUser user = userMapper.selectByAccount(key);
            userCache.put(key, user);
            return user;
        }

        /** 角色码 → 角色（{@code sys_role}，导入**不创建角色**，§2.3）。 */
        public SysRole roleByCode(String code) {
            if (code == null || code.isBlank()) {
                return null;
            }
            String key = code.trim();
            if (roleCache.containsKey(key)) {
                return roleCache.get(key);
            }
            SysRole role = roleMapper.selectByCode(key);
            roleCache.put(key, role);
            return role;
        }

        /** 全部角色码（报告里回显「已初始化角色集」，便于核对白名单）。 */
        public List<String> roleCodes() {
            java.util.List<String> codes = new java.util.ArrayList<>();
            for (SysRole role : roleMapper.selectAll(null, null)) {
                codes.add(role.getCode());
            }
            return codes;
        }

        public SysOrgLeader leaderBinding(Long orgId, Long userId, String leaderType, String category) {
            return leaderMapper.selectBinding(orgId, userId, leaderType, category);
        }

        public List<SysOrgLeader> leadersOf(Long orgId, String category) {
            return leaderMapper.selectBindings(orgId, category);
        }

        public SysUserPosition position(Long userId, Long orgId) {
            return positionMapper.selectByUserAndOrg(userId, orgId);
        }

        public List<SysUserPosition> positionsOf(Long userId) {
            return positionMapper.selectByUserId(userId);
        }

        /** 角色分配的唯一键命中 id（{@code scope_org_key = IFNULL(scope_org_id, 0)}）；未命中返回 {@code null}。 */
        public Long countAssignment(Long userId, Long roleId, long scopeOrgKey) {
            return userRoleMapper.selectIdByUniqueKey(userId, roleId, scopeOrgKey);
        }

        /** 角色分配的备注（用于「无变更即跳过」比对）。 */
        public String assignmentRemark(Long assignmentId) {
            return assignmentId == null ? null : userRoleMapper.selectRemarkById(assignmentId);
        }
    }

    /** 组织 id → 组织（服务层复用；与 {@link Snapshot} 同源但独立构建）。 */
    public Map<Long, SysOrg> orgsById() {
        Map<Long, SysOrg> byId = new LinkedHashMap<>();
        for (SysOrg org : orgMapper.selectAll(true)) {
            byId.put(org.getId(), org);
        }
        return byId;
    }
}
