package com.oa.authz.visibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导出字段级限制 —— <b>1.6「金额不可导出」（{@code oa.authz.visibility.export}）的唯一判定实现</b>。
 *
 * <h2>三条硬规则</h2>
 * <ol>
 *   <li><b>主数据导出仅系统管理员</b>（组织/人员/负责人/岗位/角色分配，import-spec §9.2 T-11）；</li>
 *   <li><b>金额列默认剔除</b>：单据列表的 {@code amount}、审计日志 JSON 内的金额键一律不进导出物。
 *       仅当调用人具备金额导出权（系统管理员或财务角色，PRD §5.3）**且**运行期开关
 *       {@code oa.authz.export.amount-enabled=true} 时才保留 —— 开关默认关闭，
 *       这是本工作包「导出接口必须剔除金额列」的落地口径；</li>
 *   <li><b>列清单只有一个来源</b>：任何 CSV 导出的表头都必须取自 {@link ExportTarget#allColumns()}，
 *       再经 {@link #columnsFor} 过滤，禁止各导出接口自己拼列名。</li>
 * </ol>
 *
 * <p><b>fail-closed 细节</b>：审计日志导出遇到无法解析的 JSON 值时，不「原样带出去」，
 * 而是整列替换为「不可解析」标记（见 {@link #redactAmountKeys(String)}）——
 * 宁可少给一列，也不能把金额漏出。
 */
public final class ExportFieldPolicy {

    /** 金额键被剔除后的占位值（审计日志 JSON 列）。 */
    public static final String REDACTED = "[已按金额导出限制剔除]";

    /** 无法解析的 JSON 列的整列占位值（fail-closed）。 */
    public static final String UNPARSEABLE = "[JSON 不可解析，已整列剔除]";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExportFieldPolicy() {
    }

    /**
     * 目标的有效导出列。
     *
     * @param target          导出目标
     * @param amountExported  是否允许导出金额（由 {@link #amountExportable} 判定）
     */
    public static List<String> columnsFor(ExportTarget target, boolean amountExported) {
        List<String> columns = new ArrayList<>();
        for (String column : target.allColumns()) {
            if (!amountExported && target.amountBearingColumns().contains(column)) {
                continue;
            }
            columns.add(column);
        }
        return columns;
    }

    /** 目标被策略剔除的列（供 {@code export-check} 出参展示，便于前端隐藏「导出金额」入口）。 */
    public static List<String> excludedColumns(ExportTarget target, boolean amountExported) {
        List<String> excluded = new ArrayList<>();
        if (!amountExported) {
            excluded.addAll(target.amountBearingColumns());
        }
        return excluded;
    }

    /** 该角色对该目标是否有导出权限（不通过即 403）。 */
    public static void assertAllowed(CurrentUser principal, ExportTarget target) {
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        boolean admin = principal.hasRole(VisibilityRoles.ADMIN);
        if (target.masterData()) {
            // 主数据导出：仅系统管理员（import-spec §9.2 T-11）
            if (!admin) {
                throw new BizException(ErrorCode.EXPORT_DENIED,
                        "主数据（" + target.code() + "）导出仅系统管理员可用（import-spec §9.2）");
            }
            return;
        }
        // 单据/审计类导出：系统管理员 + 财务角色（PRD §5.3 / REQ-AUTH-003）
        if (!admin && !AmountFieldPolicy.canExportAmounts(principal)) {
            throw new BizException(ErrorCode.EXPORT_DENIED,
                    "导出仅系统管理员与财务角色可用（PRD §5.3 / REQ-AUTH-003）：" + target.code());
        }
    }

    /**
     * 金额是否随本次导出下发。
     *
     * @param amountEnabled 运行期开关 {@code oa.authz.export.amount-enabled}（默认 false = 一律剔除）
     */
    public static boolean amountExportable(CurrentUser principal, ExportTarget target, boolean amountEnabled) {
        if (!amountEnabled || target.amountBearingColumns().isEmpty()) {
            return false;
        }
        return AmountFieldPolicy.canExportAmounts(principal);
    }

    /**
     * 剔除 JSON 文本内**任意层级**的金额键（审计日志 {@code before_json} / {@code after_json}）。
     *
     * <p>解析失败 → 返回 {@link #UNPARSEABLE}（fail-closed），不把不可审计的内容原样带出。
     */
    public static String redactAmountKeys(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode redacted = redactNode(root);
            return MAPPER.writeValueAsString(redacted);
        } catch (Exception ex) {
            return UNPARSEABLE;
        }
    }

    /** 递归剔除金额键（对象键命中 {@link AmountFieldPolicy#isAmountField} 即替换为 {@link #REDACTED}）。 */
    private static JsonNode redactNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode copy = MAPPER.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                if (AmountFieldPolicy.isAmountField(entry.getKey())) {
                    copy.put(entry.getKey(), REDACTED);
                } else {
                    copy.set(entry.getKey(), redactNode(entry.getValue()));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            ArrayNode array = MAPPER.createArrayNode();
            for (JsonNode item : node) {
                array.add(redactNode(item));
            }
            return array;
        }
        return node;
    }

    /** 导出前鉴权结果（{@code POST /api/v1/authz/export-check} 的出参）。 */
    public record ExportDecision(
            String target,
            boolean allowed,
            boolean masterData,
            List<String> effectiveColumns,
            List<String> excludedFields,
            boolean amountExportEnabled,
            boolean amountExported,
            String reason) {
    }

    /** 组装 {@code export-check} 判定结果（不抛异常，供「预览按钮是否可用」的调用方使用）。 */
    public static ExportDecision decide(CurrentUser principal, ExportTarget target, boolean amountEnabled) {
        boolean amountExported = amountExportable(principal, target, amountEnabled);
        boolean allowed;
        String reason;
        try {
            assertAllowed(principal, target);
            allowed = true;
            reason = target.masterData()
                    ? "主数据导出：仅系统管理员（import-spec §9.2）"
                    : "单据/审计导出：系统管理员与财务角色（PRD §5.3）";
        } catch (BizException ex) {
            allowed = false;
            reason = ex.getMessage();
        }
        return new ExportDecision(
                target.code(),
                allowed,
                target.masterData(),
                allowed ? columnsFor(target, amountExported) : List.of(),
                excludedColumns(target, amountExported),
                amountEnabled,
                amountExported,
                reason);
    }

    /**
     * 校验调用方提交的导出字段清单是否越界（例如前端伪造 {@code fields=["amount"]}）。
     *
     * @throws BizException 403 字段不在可导出列内
     */
    public static void assertFieldsExportable(ExportTarget target, List<String> fields, boolean amountExported) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        Set<String> allowed = new LinkedHashSet<>(columnsFor(target, amountExported));
        List<String> illegal = new ArrayList<>();
        for (String field : fields) {
            if (field != null && !allowed.contains(field)) {
                illegal.add(field);
            }
        }
        if (!illegal.isEmpty()) {
            throw new BizException(ErrorCode.EXPORT_FIELD_DENIED,
                    "以下字段不在可导出列内，已拒绝该导出请求：" + String.join("、", illegal)
                            + "（目标 " + target.code() + "）");
        }
    }

    /** 全部目标的策略快照（{@code GET /api/v1/authz/export-policy}）。 */
    public static Map<String, Object> describe(CurrentUser principal, boolean amountEnabled) {
        List<Map<String, Object>> targets = new ArrayList<>();
        for (ExportTarget target : ExportTarget.values()) {
            ExportDecision decision = decide(principal, target, amountEnabled);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("target", decision.target());
            item.put("masterData", decision.masterData());
            item.put("allowed", decision.allowed());
            item.put("effectiveColumns", decision.effectiveColumns());
            item.put("excludedFields", decision.excludedFields());
            targets.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("amountExportEnabled", amountEnabled);
        result.put("amountExportRoles", List.of(VisibilityRoles.ADMIN, VisibilityRoles.FINANCE_OWNER));
        result.put("masterDataExportRoles", List.of(VisibilityRoles.ADMIN));
        result.put("targets", targets);
        return result;
    }
}
