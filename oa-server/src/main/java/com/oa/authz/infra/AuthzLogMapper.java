package com.oa.authz.infra;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 权限变更留痕查询 Mapper（REQ-LOG-004 / AC-20 / AC-59）。
 *
 * <p>数据源是**只追加**的 {@code sys_log}（写入侧由 {@code @Audited} + {@code AuditLogWriter}
 * 负责，本接口**只读**）。{@code sys_log} 不是受控表，无 {@code @dataScope} 标记；
 * 不继承 {@code BaseMapper}。
 *
 * <p>过滤范围：{@code target_type} 落在权限域的八类目标上（角色 / 角色权限 / 数据域 /
 * 类别 / 组织节点 / 权限树节点 / 用户角色 / 权限缓存），保证该接口**只**暴露权限变更，
 * 不泄漏其他业务的操作日志。
 */
@Mapper
public interface AuthzLogMapper {

    /** 权限域目标类型白名单（与各 Controller 的 {@code @Audited(targetType=...)} 一一对应）。 */
    String AUTHZ_TARGET_TYPES =
            "'role','role_permission','role_data_scope','role_category','role_org_node',"
                    + "'permission','user_role','permission_cache'";

    @Select({"<script>",
            "SELECT id, user_id AS userId, user_name AS userName, action, target_type AS targetType,",
            "       target_id AS targetId, before_json AS beforeJson, after_json AS afterJson,",
            "       ip, created_at AS createdAt",
            "FROM sys_log",
            "WHERE target_type IN (" + AUTHZ_TARGET_TYPES + ")",
            "<if test='action != null and action != \"\"'> AND action = #{action}</if>",
            "<if test='targetType != null and targetType != \"\"'> AND target_type = #{targetType}</if>",
            "<if test='userId != null'> AND user_id = #{userId}</if>",
            "ORDER BY id DESC",
            "LIMIT #{offset}, #{size}",
            "</script>"})
    List<AuthzLogRow> selectPage(@Param("action") String action,
                                 @Param("targetType") String targetType,
                                 @Param("userId") Long userId,
                                 @Param("offset") long offset,
                                 @Param("size") long size);

    @Select({"<script>",
            "SELECT COUNT(1) FROM sys_log",
            "WHERE target_type IN (" + AUTHZ_TARGET_TYPES + ")",
            "<if test='action != null and action != \"\"'> AND action = #{action}</if>",
            "<if test='targetType != null and targetType != \"\"'> AND target_type = #{targetType}</if>",
            "<if test='userId != null'> AND user_id = #{userId}</if>",
            "</script>"})
    long count(@Param("action") String action,
               @Param("targetType") String targetType,
               @Param("userId") Long userId);

    /** 权限变更记录行。 */
    class AuthzLogRow {

        private Long id;
        private Long userId;
        private String userName;
        private String action;
        private String targetType;
        private Long targetId;
        private String beforeJson;
        private String afterJson;
        private String ip;
        private LocalDateTime createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getTargetType() {
            return targetType;
        }

        public void setTargetType(String targetType) {
            this.targetType = targetType;
        }

        public Long getTargetId() {
            return targetId;
        }

        public void setTargetId(Long targetId) {
            this.targetId = targetId;
        }

        public String getBeforeJson() {
            return beforeJson;
        }

        public void setBeforeJson(String beforeJson) {
            this.beforeJson = beforeJson;
        }

        public String getAfterJson() {
            return afterJson;
        }

        public void setAfterJson(String afterJson) {
            this.afterJson = afterJson;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
