package com.oa.authz.infra;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据域装载 Mapper（只读）。
 *
 * <p>职责：从 {@code sys_user} + {@code sys_role} + {@code sys_user_role} + {@code sys_role_category} +
 * {@code sys_org} 读取解析数据域所需的最小字段（doc/tech-design.md §5.3 第 1 步）。
 * 本 Mapper 的查询天然自限（按主键/用户 id 取本人配置），已列入 {@code oa.scope.exempt-statement-ids} 免拦截清单。
 *
 * <p>注意：{@code sys_user_role.scope_org_key} 是 GENERATED 列，**只读不写**，本骨架不写入该表。
 */
@Mapper
public interface DataScopeMapper {

    /** 登录人最小信息（含 {@code last_login_at} 用于首登强制改密判定）。 */
    @Select("SELECT id, account, name, employee_no AS employeeNo, org_id AS orgId, company_id AS companyId,"
            + " status, last_login_at AS lastLoginAt FROM sys_user"
            + " WHERE id = #{userId} AND deleted_at IS NULL LIMIT 1")
    UserBriefRow selectUserBrief(@Param("userId") Long userId);

    /** 用户的角色与数据域。 */
    @Select("SELECT r.code AS code, r.data_scope AS dataScope, ur.scope_org_id AS scopeOrgId"
            + " FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id"
            + " WHERE ur.user_id = #{userId} AND r.deleted_at IS NULL")
    List<UserRoleRow> selectRoles(@Param("userId") Long userId);

    /** 集团分管领导按分管业务线绑定的类别集合。 */
    @Select("SELECT rc.category FROM sys_role_category rc"
            + " JOIN sys_user_role ur ON ur.role_id = rc.role_id"
            + " WHERE ur.user_id = #{userId}")
    List<String> selectCategories(@Param("userId") Long userId);

    /** 组织节点（取 path 做子树前缀过滤）。 */
    @Select("SELECT id, name, path, org_type AS orgType FROM sys_org WHERE id = #{orgId} AND deleted_at IS NULL LIMIT 1")
    OrgRow selectOrg(@Param("orgId") Long orgId);

    /** 按名称兜底定位归口部门（集团财务部）。 */
    @Select("SELECT id FROM sys_org WHERE name = #{name} AND org_type = 'dept' AND deleted_at IS NULL"
            + " ORDER BY depth ASC, id ASC LIMIT 1")
    Long selectOrgIdByName(@Param("name") String name);

    /** 用户最小信息行。 */
    class UserBriefRow {

        private Long id;
        private String account;
        private String name;
        private String employeeNo;
        private Long orgId;
        private Long companyId;
        private String status;
        private LocalDateTime lastLoginAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getAccount() {
            return account;
        }

        public void setAccount(String account) {
            this.account = account;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmployeeNo() {
            return employeeNo;
        }

        public void setEmployeeNo(String employeeNo) {
            this.employeeNo = employeeNo;
        }

        public Long getOrgId() {
            return orgId;
        }

        public void setOrgId(Long orgId) {
            this.orgId = orgId;
        }

        public Long getCompanyId() {
            return companyId;
        }

        public void setCompanyId(Long companyId) {
            this.companyId = companyId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getLastLoginAt() {
            return lastLoginAt;
        }

        public void setLastLoginAt(LocalDateTime lastLoginAt) {
            this.lastLoginAt = lastLoginAt;
        }
    }

    /** 角色行。 */
    class UserRoleRow {

        private String code;
        private String dataScope;
        private Long scopeOrgId;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDataScope() {
            return dataScope;
        }

        public void setDataScope(String dataScope) {
            this.dataScope = dataScope;
        }

        public Long getScopeOrgId() {
            return scopeOrgId;
        }

        public void setScopeOrgId(Long scopeOrgId) {
            this.scopeOrgId = scopeOrgId;
        }
    }

    /** 组织行。 */
    class OrgRow {

        private Long id;
        private String name;
        private String path;
        private String orgType;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getOrgType() {
            return orgType;
        }

        public void setOrgType(String orgType) {
            this.orgType = orgType;
        }
    }
}
