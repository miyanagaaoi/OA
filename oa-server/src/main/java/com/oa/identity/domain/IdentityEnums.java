package com.oa.identity.domain;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 身份与组织领域的**枚举字典集中处**（组织类型 / 组织状态 / 人员状态 / 负责人类型 / 事项类别 / 主岗标识）。
 *
 * <p>存在意义（对应施工要求第 8 条「枚举中文↔code 转换集中在一处，不要散落 magic string」）：
 * <ul>
 *   <li>所有 code 字面量只在本文件出现一次，其它类一律引用 {@code IdentityEnums.*.CODE}；</li>
 *   <li>中文标签 ↔ code 的映射与 doc/import-spec.md §3、doc/enums.md §14 逐条对齐；</li>
 *   <li>{@link #parse(String)} 同时接受 **code**（{@code group}）与 **中文标签**（{@code 集团}），
 *       因为接口入参要与导入模板口径（中文）以及前端/内部调用（code）都能通。</li>
 * </ul>
 *
 * <p><b>与 DDL 的对应</b>（doc/data-model.md §2，DDL 为唯一权威）：
 * <ul>
 *   <li>{@code sys_org.org_type}：{@code group/company/dept/section}（列宽 16）；</li>
 *   <li>{@code sys_org.status}：{@code active/disabled}；</li>
 *   <li>{@code sys_user.status}：{@code active/disabled/resigned}（导入模板只有 在职/离职，停用走后台单条操作 T-05）；</li>
 *   <li>{@code sys_org_leader.leader_type}：{@code primary/deputy}；</li>
 *   <li>{@code sys_org_leader.category}：事项类别五值（导入模板 {@code business_line} 中文标签）。</li>
 * </ul>
 */
public final class IdentityEnums {

    private IdentityEnums() {
    }

    /** 带 code / 中文标签的枚举统一契约。 */
    public interface Coded {

        /** 落库 code。 */
        String code();

        /** 中文标签（与导入模板 / PRD 用语一致）。 */
        String label();
    }

    // ================================================================ 组织类型

    /**
     * 组织类型（{@code sys_org.org_type}）。
     *
     * <p>{@link #depth} 与 DDL 注释「层级：1集团 2公司 3部门 4科室」一一对应，
     * 四级层级约束的判定基准就是它（见 {@code OrgHierarchy#assertParentChild}）。
     */
    public enum OrgType implements Coded {

        /** 集团（根节点，{@code parent_id IS NULL}）。 */
        GROUP("group", "集团", 1),
        /** 公司。 */
        COMPANY("company", "公司", 2),
        /** 部门（可挂公司，也可挂集团＝集团职能部门，PRD 5.1 / import-spec E-ORG-006）。 */
        DEPT("dept", "部门", 3),
        /** 科室（必须挂部门）。 */
        SECTION("section", "科室", 4);

        private final String code;
        private final String label;
        private final int depth;

        OrgType(String code, String label, int depth) {
            this.code = code;
            this.label = label;
            this.depth = depth;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String label() {
            return label;
        }

        /** 层级：1集团 / 2公司 / 3部门 / 4科室。 */
        public int depth() {
            return depth;
        }

        public static OrgType ofCode(String code) {
            return lookup(values(), code);
        }

        public static OrgType parse(String value) {
            return require(values(), value, "组织类型");
        }
    }

    // ================================================================ 状态

    /** 组织状态（{@code sys_org.status}）。 */
    public enum OrgStatus implements Coded {

        ACTIVE("active", "启用"),
        DISABLED("disabled", "停用");

        private final String code;
        private final String label;

        OrgStatus(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String label() {
            return label;
        }

        public static OrgStatus ofCode(String code) {
            return lookup(values(), code);
        }

        public static OrgStatus parse(String value) {
            return require(values(), value, "组织状态");
        }
    }

    /**
     * 人员状态（{@code sys_user.status}）。
     *
     * <p>导入模板只允许 {@code 在职/离职}（T-05 定稿：停用不通过批量导入设置），
     * 因此 {@link #DISABLED} 只能由后台单条接口设置。
     */
    public enum UserStatus implements Coded {

        ACTIVE("active", "在职"),
        DISABLED("disabled", "停用"),
        RESIGNED("resigned", "离职");

        private final String code;
        private final String label;

        UserStatus(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String label() {
            return label;
        }

        public static UserStatus ofCode(String code) {
            return lookup(values(), code);
        }

        public static UserStatus parse(String value) {
            return require(values(), value, "人员状态");
        }
    }

    // ================================================================ 负责人

    /** 负责人类型（{@code sys_org_leader.leader_type}）。 */
    public enum LeaderType implements Coded {

        PRIMARY("primary", "正职"),
        DEPUTY("deputy", "副职");

        private final String code;
        private final String label;

        LeaderType(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String label() {
            return label;
        }

        public static LeaderType ofCode(String code) {
            return lookup(values(), code);
        }

        public static LeaderType parse(String value) {
            return require(values(), value, "负责人类型");
        }
    }

    // ================================================================ 事项类别（业务线）

    /**
     * 事项类别五值（{@code sys_org_leader.category}，集团分管领导按业务线绑定）。
     *
     * <p>权威：doc/enums.md §14 与 doc/import-spec.md §3.4——
     * 中文标签 {@code 经营/经济/行政/人力/投资} ↔ {@code business/economy/admin/hr/invest}；
     * <b>业务线直接复用事项类别五值，不新增枚举</b>（T-06 定稿）。
     *
     * <p>旧码 {@code operate} 作废并迁移为 {@code business}（enums.md §14）：本类在解析时
     * **兼容旧码**（视为 {@code business}），落库一律写新码，避免历史数据解析失败。
     */
    public enum Category implements Coded {

        BUSINESS("business", "经营"),
        ECONOMY("economy", "经济"),
        ADMIN("admin", "行政"),
        HR("hr", "人力"),
        INVEST("invest", "投资");

        /** 已作废的旧码（enums.md §14）：解析时归一为 {@link #BUSINESS}。 */
        public static final String LEGACY_OPERATE = "operate";

        private final String code;
        private final String label;

        Category(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String label() {
            return label;
        }

        public static Category ofCode(String code) {
            if (code != null && LEGACY_OPERATE.equalsIgnoreCase(code.trim())) {
                return BUSINESS;
            }
            return lookup(values(), code);
        }

        public static Category parse(String value) {
            if (value == null || value.isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "事项类别（业务线）不能为空，取值：经营/经济/行政/人力/投资");
            }
            Category category = ofCode(value);
            if (category != null) {
                return category;
            }
            category = lookupByLabel(values(), value);
            if (category == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未知事项类别（业务线）：" + value + "，取值：经营/经济/行政/人力/投资");
            }
            return category;
        }

        /** 全部中文标签（供前端下拉与导出）。 */
        public static List<String> labels() {
            List<String> result = new ArrayList<>();
            for (Category category : values()) {
                result.add(category.label);
            }
            return result;
        }
    }

    // ================================================================ 主岗标识

    /** 主岗标识（{@code sys_user_position.is_primary}，TINYINT(1)）。 */
    public enum PrimaryFlag implements Coded {

        PRIMARY("1", "是"),
        NON_PRIMARY("0", "否");

        private final String code;
        private final String label;

        PrimaryFlag(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String label() {
            return label;
        }

        public int value() {
            return this == PRIMARY ? 1 : 0;
        }

        public static PrimaryFlag of(boolean primary) {
            return primary ? PRIMARY : NON_PRIMARY;
        }

        /** 解析导入模板的 是/否，或 1/0、true/false。 */
        public static PrimaryFlag parse(String value) {
            if (value == null || value.isBlank()) {
                return NON_PRIMARY;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "是", "1", "true", "yes", "y" -> PRIMARY;
                case "否", "0", "false", "no", "n" -> NON_PRIMARY;
                default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知主岗标识：" + value + "，取值：是/否");
            };
        }
    }

    // ================================================================ 内部工具

    private static <E extends Enum<E> & Coded> E lookup(E[] values, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (E value : values) {
            if (value.code().equals(normalized)) {
                return value;
            }
        }
        return null;
    }

    private static <E extends Enum<E> & Coded> E lookupByLabel(E[] values, String label) {
        if (label == null) {
            return null;
        }
        String normalized = label.trim();
        for (E value : values) {
            if (value.label().equals(normalized)) {
                return value;
            }
        }
        return null;
    }

    /** 宽松解析：先 code 再中文标签；都不中即 400。 */
    private static <E extends Enum<E> & Coded> E require(E[] values, String value, String what) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, what + "不能为空");
        }
        E found = lookup(values, value);
        if (found == null) {
            found = lookupByLabel(values, value);
        }
        if (found == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知" + what + "：" + value);
        }
        return found;
    }

    /** code → 中文标签（未知 code 原样返回，避免历史脏数据导致 500）。 */
    public static String labelOf(OrgType type) {
        return type == null ? null : type.label();
    }

    public static String labelOf(OrgStatus status) {
        return status == null ? null : status.label();
    }

    public static String labelOf(UserStatus status) {
        return status == null ? null : status.label();
    }

    public static String labelOf(LeaderType type) {
        return type == null ? null : type.label();
    }

    public static String labelOf(Category category) {
        return category == null ? null : category.label();
    }
}
