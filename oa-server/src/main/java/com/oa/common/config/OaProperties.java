package com.oa.common.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final Identity identity = new Identity();
    private final Watermark watermark = new Watermark();
    private final Authz authz = new Authz();
    private final Workflow workflow = new Workflow();

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

    public Identity getIdentity() {
        return identity;
    }

    public Watermark getWatermark() {
        return watermark;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    /**
     * 流程引擎（阶段 2a）。
     *
     * <p>注意分层：**运行期可配置项**（决议模式、Q6/Q7 闸门次数与补件时限等）由
     * 管理后台按模板维护（落在 {@code flow_template} 的闸门列上，见 doc/templates.md §1.7），
     * **不进本类**；本类只放「引擎行为开关」这类环境级配置。
     */
    public static class Workflow {

        private final Approver approver = new Approver();

        public Approver getApprover() {
            return approver;
        }

        /** 审批人解析相关。 */
        public static class Approver {

            /**
             * 是否允许「同一人连续担任多个串行节点审批人时自动合并」。
             *
             * <p><b>默认 false</b>：doc/prd-0.1.md §5.4 与 doc/enums.md §3 共同约束第 4 条
             * 明确「默认逐节点分别审批（不做连续节点自动合并），如需合并由流程设计器显式配置」。
             * 文档没有给出「设计器显式配置」的落点（{@code flow_template}/{@code flow_node}
             * 都没有该列），因此本开关是**系统级**配置，并按「待决策项」交付：
             * 若业务确认要「按模板/按节点勾选」，需新增配置列。
             */
            private boolean mergeConsecutiveNodes = false;

            /** 单次解析最多输出的候选人条数（防御性上限，避免异常配置把快照撑爆）。 */
            private int maxCandidatesPerNode = 200;

            public boolean isMergeConsecutiveNodes() {
                return mergeConsecutiveNodes;
            }

            public void setMergeConsecutiveNodes(boolean mergeConsecutiveNodes) {
                this.mergeConsecutiveNodes = mergeConsecutiveNodes;
            }

            public int getMaxCandidatesPerNode() {
                return maxCandidatesPerNode;
            }

            public void setMaxCandidatesPerNode(int maxCandidatesPerNode) {
                this.maxCandidatesPerNode = maxCandidatesPerNode;
            }
        }
    }

    public Authz getAuthz() {
        return authz;
    }

    /**
     * 字段级限制（阶段 1.6，{@code oa.authz.*}）。
     *
     * <p>PRD §5.3 明文规定「金额对非财务类角色不可导出」；V0.4 又给出「系统管理员与财务角色可导出」
     * 的例外。本开关决定**例外的开关状态**：默认 {@code false} = 一切角色都不导出金额列
     * （最严口径，本工作包的验收口径）；置 {@code true} 时仅系统管理员与财务角色可随导出拿到金额列。
     */
    public static class Authz {

        private final Export export = new Export();

        public Export getExport() {
            return export;
        }

        /** 导出策略开关。 */
        public static class Export {

            /** 是否允许系统管理员/财务角色导出金额列（默认关闭）。 */
            private boolean amountEnabled = false;

            public boolean isAmountEnabled() {
                return amountEnabled;
            }

            public void setAmountEnabled(boolean amountEnabled) {
                this.amountEnabled = amountEnabled;
            }
        }
    }

    /**
     * 水印（REQ-USER-004 / AC-44，{@code oa.watermark.*}）。
     *
     * <p>透明度区间 <b>5%–8%</b> 是设计常量（见 {@code com.oa.identity.app.WatermarkPolicy}
     * 的 {@code MIN_OPACITY_PERCENT} / {@code MAX_OPACITY_PERCENT}，来源 DESIGN.md），
     * 本类只放**环境级**的默认值：任何来源的透明度都必须被夹到该区间内（越界即夹紧，不放行）。
     */
    public static class Watermark {

        /** 水印开关的全局默认值（个人偏好接口落地前，服务端给前端的默认口径）。 */
        private boolean enabled = true;

        /** 水印透明度默认值（0.05–0.08；默认取区间中值 0.06）。 */
        private double opacity = 0.06;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getOpacity() {
            return opacity;
        }

        public void setOpacity(double opacity) {
            this.opacity = opacity;
        }
    }

    /**
     * 组织与人员（阶段 1.1 / 1.2，{@code oa.identity.*}）。
     *
     * <p>注意：PRD §9.1 的「运行期可配置项」（阈值、闸门次数、补件上限）由管理后台维护，
     * 不进本类；这里只放**环境级**开关。
     */
    public static class Identity {

        /**
         * 在途/待办命中时是否**拒绝**操作（默认 {@code true}）。
         *
         * <p>依据 AC-11/AC-12、PRD §5.5、import-spec §8.1/§8.2：「离职前必须清空名下待办」
         * 「组织停用前必须清空在途单据」必须**阻断**；置为 {@code false} 时仅告警放行
         * （返回影响清单，由前端提示二次确认），用于上线初期的过渡期。
         */
        private boolean blockOnInflight = true;

        public boolean isBlockOnInflight() {
            return blockOnInflight;
        }

        public void setBlockOnInflight(boolean blockOnInflight) {
            this.blockOnInflight = blockOnInflight;
        }
    }

    /** Web 层配置。 */
    public static class Web {

        /**
         * 系统标题（非敏感运行期配置，供 {@code GET /api/v1/auth/client-config} 下发；
         * 前端用于浏览器标题与登录页品牌区，值必须与 {@code oa-web/.env.*} 的
         * {@code VITE_APP_TITLE} 保持一致）。
         */
        private String title = "集团OA审批系统";

        /**
         * 前端请求的统一 API 前缀（非敏感；与 {@code oa-web} 的
         * {@code VITE_API_BASE_URL} 默认值 {@code /api/v1} 一致）。
         *
         * <p>只下发**相对前缀**：绝对地址（协议/主机/端口）由浏览器按当前来源解析，
         * 既避免把内网拓扑写进响应，也让 dev（Vite 代理）与生产（Nginx 反代）共用同一口径。
         */
        private String apiBaseUrl = "/api/v1";

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

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
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

        /**
         * 免拦截的 MappedStatement id 前缀（身份/会话/字典等天然自限的查询）。
         *
         * <p>口径（评审阻断项）：只允许「认证前查询」与「非业务数据表」整体豁免；
         * **禁止**豁免 {@code selectOne/selectList/selectById/selectPage/selectCount} 这类通用读方法
         * —— 它们等价于给受控表开后门。受控表 Mapper（{@code SysUserMapper}/{@code SysOrgMapper}/
         * {@code SysOrgLeaderMapper}/{@code SysUserPositionMapper}）一律不继承 {@code BaseMapper}，
         * 因此这些注入语句本就不存在，见 {@code SysUserMapper} 类注释。
         *
         * <p>唯一的「业务表窄豁免」是账号/工号**唯一性判重**（{@code countByAccountSystem} /
         * {@code countByEmployeeNoSystem}，阶段 1 收口）：唯一性是全局约束，与数据域无关；
         * 两条语句只返回 {@code COUNT}、不返回行数据，因此不构成数据域读取旁路
         * （读取口径 {@code selectUserById/selectUserPage/selectDirectoryUsers/selectForExport}
         * 一行未动）。守卫：{@code DataScopeMapperGuardTest}。
         */
        private List<String> exemptStatementIds = new ArrayList<>(List.of(
                "com.oa.identity.infra.SysUserMapper.selectByAccount",
                "com.oa.identity.infra.SysUserMapper.countByAccountSystem",
                "com.oa.identity.infra.SysUserMapper.countByEmployeeNoSystem",
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

        /**
         * 敏感字段加密的**活动密钥**（Base64 或 hex，32 字节 = AES-256）。
         *
         * <p><b>生产一律由环境变量注入</b>（{@code OA_PHONE_KEY}），仓库内不得出现真实密钥：
         * 缺失时 {@code PhoneCipher} 在启动期直接抛错（fail-fast），**不允许静默降级为明文存储**
         * （PRD §5.3「手机号加密存储」+ REQ-NFR-005）。
         */
        private String phoneKey;

        /**
         * 活动密钥的**替代来源**：指向一个只含密钥（首行，支持 {@code #} 注释）的文件。
         *
         * <p>用途：本机 dev 免去「每次重启都要注入环境变量」的麻烦，密钥文件放在仓库之外的
         * 用户目录（默认 {@code ${user.home}/.oa/oa-phone.key}）。生产不配置本项。
         */
        private String phoneKeyFile;

        /** 活动密钥的 keyId（密文内会带该 id，便于将来轮换，默认 {@code k1}）。 */
        private String phoneKeyId = "k1";

        /**
         * **历史密钥**（keyId → 密钥材料），仅用于解密（轮换期新旧密文共存）。
         *
         * <p>示例：{@code oa.security.phone-keys.k0=<旧密钥>}；轮换流程见
         * {@code PhoneCryptoService#rotateToActiveKey()}。
         */
        private Map<String, String> phoneKeys = new LinkedHashMap<>();

        public String getPhoneKey() {
            return phoneKey;
        }

        public void setPhoneKey(String phoneKey) {
            this.phoneKey = phoneKey;
        }

        public String getPhoneKeyFile() {
            return phoneKeyFile;
        }

        public void setPhoneKeyFile(String phoneKeyFile) {
            this.phoneKeyFile = phoneKeyFile;
        }

        public String getPhoneKeyId() {
            return phoneKeyId;
        }

        public void setPhoneKeyId(String phoneKeyId) {
            this.phoneKeyId = phoneKeyId;
        }

        public Map<String, String> getPhoneKeys() {
            return phoneKeys;
        }

        public void setPhoneKeys(Map<String, String> phoneKeys) {
            this.phoneKeys = phoneKeys == null ? new LinkedHashMap<>() : new LinkedHashMap<>(phoneKeys);
        }

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
