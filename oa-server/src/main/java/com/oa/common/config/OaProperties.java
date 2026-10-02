package com.oa.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 工程级配置（前缀 {@code oa}），与 doc/tech-design.md §7「配置分层」对应：
 * 本类是**环境配置**；运行期可配置项（阈值、闸门次数、补件上限等）由管理后台维护，不进本类。
 *
 * <p>所有默认值即文档定稿值（REQ-NFR-005/006、§5.4、§5.5）。
 */
@ConfigurationProperties(prefix = "oa")
public class OaProperties {

    private final Web web = new Web();
    private final Session session = new Session();
    private final Scope scope = new Scope();
    private final Security security = new Security();
    private final Db db = new Db();
    private final Jackson jackson = new Jackson();

    public Web getWeb() {
        return web;
    }

    public Session getSession() {
        return session;
    }

    public Scope getScope() {
        return scope;
    }

    public Security getSecurity() {
        return security;
    }

    public Db getDb() {
        return db;
    }

    public Jackson getJackson() {
        return jackson;
    }

    /** Web 层配置。 */
    public static class Web {

        /** 是否开启 CORS。**生产必须关闭**（前后端同源经 Nginx 反代，doc/tech-design.md §2）。 */
        private boolean corsEnabled = false;

        /** 允许的来源（仅 dev 使用）。 */
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173"));

        /** 认证拦截器排除路径（登录、口令策略、健康检查等）。 */
        private List<String> permitAll = new ArrayList<>(List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/password-policy",
                "/api/v1/auth/lock-status",
                "/actuator/**",
                "/error"
        ));

        public boolean isCorsEnabled() {
            return corsEnabled;
        }

        public void setCorsEnabled(boolean corsEnabled) {
            this.corsEnabled = corsEnabled;
        }

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getPermitAll() {
            return permitAll;
        }

        public void setPermitAll(List<String> permitAll) {
            this.permitAll = permitAll;
        }
    }

    /** 会话与设备（REQ-NFR-006 / REQ-USER-003）。 */
    public static class Session {

        /** 会话 Cookie 名（HttpOnly + Secure + SameSite=Lax）。 */
        private String cookieName = "OA_SESSION";

        /** 会话存储：{@code redis}（默认）或 {@code memory}（仅本地调试，重启即失效）。 */
        private String store = "redis";

        /** 同时在线设备上限，默认 3，超出踢出 login_at 最早的会话。 */
        private int maxDevices = 3;

        /** 「记住我」有效期（天）。 */
        private long rememberMeDays = 7;

        /** 未勾选「记住我」时的会话级有效期（小时）。 */
        private long temporaryHours = 12;

        /** Cookie 是否带 Secure（生产 HTTPS 必须 true）。 */
        private boolean cookieSecure = true;

        private String cookieSameSite = "Lax";

        private String cookiePath = "/";

        /** 活跃续期间隔（秒），避免每个请求都写库。 */
        private long touchIntervalSeconds = 300;

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public int getMaxDevices() {
            return maxDevices;
        }

        public void setMaxDevices(int maxDevices) {
            this.maxDevices = maxDevices;
        }

        public long getRememberMeDays() {
            return rememberMeDays;
        }

        public void setRememberMeDays(long rememberMeDays) {
            this.rememberMeDays = rememberMeDays;
        }

        public long getTemporaryHours() {
            return temporaryHours;
        }

        public void setTemporaryHours(long temporaryHours) {
            this.temporaryHours = temporaryHours;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }

        public String getCookieSameSite() {
            return cookieSameSite;
        }

        public void setCookieSameSite(String cookieSameSite) {
            this.cookieSameSite = cookieSameSite;
        }

        public String getCookiePath() {
            return cookiePath;
        }

        public void setCookiePath(String cookiePath) {
            this.cookiePath = cookiePath;
        }

        public long getTouchIntervalSeconds() {
            return touchIntervalSeconds;
        }

        public void setTouchIntervalSeconds(long touchIntervalSeconds) {
            this.touchIntervalSeconds = touchIntervalSeconds;
        }
    }

    /** 数据域（§5.3）。 */
    public static class Scope {

        /** 归口部门（集团财务部）的 org id；为空时按 {@link #financeDeptName} 查询。 */
        private Long financeDeptId;

        /** 归口部门名称（用于兜底查询 sys_org）。 */
        private String financeDeptName = "财务部";

        /** 归口部门组织路径（形如 {@code /1/30/210/}），用于通讯录子树过滤；为空时按 org id 查。 */
        private String financeDeptPath;

        /** 是否启用财务部「归口部门」分支，默认关闭（见 DataScopeContext 的说明）。 */
        private boolean financeOwnerDeptBranchEnabled = false;

        /** 是否拦截「未织入数据域过滤的裸查询」（评审阻断项）。 */
        private boolean enforceUnmarkedSelect = true;

        /** 免拦截的 MappedStatement id 前缀（身份/会话/字典等天然自限的查询）。 */
        private List<String> exemptStatementIds = new ArrayList<>(List.of(
                "com.oa.identity.infra.SysUserMapper.selectByAccount",
                "com.oa.identity.infra.SysUserMapper.selectOne",
                "com.oa.identity.infra.SysUserSessionMapper",
                "com.oa.identity.infra.SysLoginLogMapper",
                "com.oa.authz.infra.DataScopeMapper"
        ));

        /** 参与数据域过滤的表白名单（与 {@code @DataScopeTable} 注解合并）。 */
        private List<ScopeTable> tables = new ArrayList<>(List.of(
                ScopeTable.of("flow_instance", "i", "INSTANCE"),
                ScopeTable.of("form_data", "f", "INSTANCE"),
                ScopeTable.of("flow_task", "t", "INSTANCE"),
                ScopeTable.of("flow_routing", "r", "INSTANCE"),
                ScopeTable.of("sys_user", "u", "USER")
        ));

        public Long getFinanceDeptId() {
            return financeDeptId;
        }

        public void setFinanceDeptId(Long financeDeptId) {
            this.financeDeptId = financeDeptId;
        }

        public String getFinanceDeptName() {
            return financeDeptName;
        }

        public void setFinanceDeptName(String financeDeptName) {
            this.financeDeptName = financeDeptName;
        }

        public String getFinanceDeptPath() {
            return financeDeptPath;
        }

        public void setFinanceDeptPath(String financeDeptPath) {
            this.financeDeptPath = financeDeptPath;
        }

        public boolean isFinanceOwnerDeptBranchEnabled() {
            return financeOwnerDeptBranchEnabled;
        }

        public void setFinanceOwnerDeptBranchEnabled(boolean financeOwnerDeptBranchEnabled) {
            this.financeOwnerDeptBranchEnabled = financeOwnerDeptBranchEnabled;
        }

        public boolean isEnforceUnmarkedSelect() {
            return enforceUnmarkedSelect;
        }

        public void setEnforceUnmarkedSelect(boolean enforceUnmarkedSelect) {
            this.enforceUnmarkedSelect = enforceUnmarkedSelect;
        }

        public List<String> getExemptStatementIds() {
            return exemptStatementIds;
        }

        public void setExemptStatementIds(List<String> exemptStatementIds) {
            this.exemptStatementIds = exemptStatementIds;
        }

        public List<ScopeTable> getTables() {
            return tables;
        }

        public void setTables(List<ScopeTable> tables) {
            this.tables = tables;
        }
    }

    /** 数据域表白名单条目。 */
    public static class ScopeTable {

        private String table;

        private String alias = "i";

        /** {@code INSTANCE} / {@code USER} / {@code NONE}。 */
        private String kind = "INSTANCE";

        public static ScopeTable of(String table, String alias, String kind) {
            ScopeTable item = new ScopeTable();
            item.table = table;
            item.alias = alias;
            item.kind = kind;
            return item;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }
    }

    /** 口令与会话安全（REQ-NFR-005）。 */
    public static class Security {

        /** 口令最小长度：8（且必须同时含字母与数字）。 */
        private int passwordMinLength = 8;

        /** BCrypt strength，文档要求 ≥10。 */
        private int bcryptStrength = 12;

        /** 连续失败次数上限。 */
        private int loginMaxFailures = 5;

        /** 锁定时长（分钟）。 */
        private int loginLockMinutes = 15;

        /** 失败计数窗口（分钟），窗口内计数、超窗清零。 */
        private int loginFailWindowMinutes = 15;

        public int getPasswordMinLength() {
            return passwordMinLength;
        }

        public void setPasswordMinLength(int passwordMinLength) {
            this.passwordMinLength = passwordMinLength;
        }

        public int getBcryptStrength() {
            return bcryptStrength;
        }

        public void setBcryptStrength(int bcryptStrength) {
            this.bcryptStrength = bcryptStrength;
        }

        public int getLoginMaxFailures() {
            return loginMaxFailures;
        }

        public void setLoginMaxFailures(int loginMaxFailures) {
            this.loginMaxFailures = loginMaxFailures;
        }

        public int getLoginLockMinutes() {
            return loginLockMinutes;
        }

        public void setLoginLockMinutes(int loginLockMinutes) {
            this.loginLockMinutes = loginLockMinutes;
        }

        public int getLoginFailWindowMinutes() {
            return loginFailWindowMinutes;
        }

        public void setLoginFailWindowMinutes(int loginFailWindowMinutes) {
            this.loginFailWindowMinutes = loginFailWindowMinutes;
        }
    }

    /** 数据库相关（§5.5 不可篡改触发器）。 */
    public static class Db {

        /** 是否启用启动期触发器初始化；**生产可配置关闭**。 */
        private boolean immutableTriggersEnabled = true;

        /** 触发器 SQL 位置（无 DELIMITER，语句以 {@code //} 分隔）。 */
        private String immutableTriggersLocation = "classpath:db/trigger/immutable-triggers.sql";

        public boolean isImmutableTriggersEnabled() {
            return immutableTriggersEnabled;
        }

        public void setImmutableTriggersEnabled(boolean immutableTriggersEnabled) {
            this.immutableTriggersEnabled = immutableTriggersEnabled;
        }

        public String getImmutableTriggersLocation() {
            return immutableTriggersLocation;
        }

        public void setImmutableTriggersLocation(String immutableTriggersLocation) {
            this.immutableTriggersLocation = immutableTriggersLocation;
        }
    }

    /** 序列化（金额禁止浮点，金额一律字符串）。 */
    public static class Jackson {

        /** BigDecimal（金额 DECIMAL(18,2)）序列化为字符串。 */
        private boolean serializeBigDecimalAsString = true;

        /** Long/BIGINT UNSIGNED 主键序列化为字符串，规避 JS 53 位精度丢失。 */
        private boolean serializeLongAsString = true;

        private String dateFormat = "yyyy-MM-dd HH:mm:ss";

        private String timeZone = "Asia/Shanghai";

        public boolean isSerializeBigDecimalAsString() {
            return serializeBigDecimalAsString;
        }

        public void setSerializeBigDecimalAsString(boolean serializeBigDecimalAsString) {
            this.serializeBigDecimalAsString = serializeBigDecimalAsString;
        }

        public boolean isSerializeLongAsString() {
            return serializeLongAsString;
        }

        public void setSerializeLongAsString(boolean serializeLongAsString) {
            this.serializeLongAsString = serializeLongAsString;
        }

        public String getDateFormat() {
            return dateFormat;
        }

        public void setDateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
        }

        public String getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(String timeZone) {
            this.timeZone = timeZone;
        }
    }
}
