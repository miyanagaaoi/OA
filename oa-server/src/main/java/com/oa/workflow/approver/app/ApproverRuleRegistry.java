package com.oa.workflow.approver.app;

import com.oa.common.config.OaProperties;
import com.oa.workflow.approver.app.rules.BranchLeaderRule;
import com.oa.workflow.approver.app.rules.ChairmanRule;
import com.oa.workflow.approver.app.rules.CollabDeptLeaderRule;
import com.oa.workflow.approver.app.rules.DeptLeaderUpwardRule;
import com.oa.workflow.approver.app.rules.DesignatedRule;
import com.oa.workflow.approver.app.rules.FinanceOwnerRule;
import com.oa.workflow.approver.app.rules.GroupLeaderRule;
import com.oa.workflow.approver.app.rules.InitiatorPickRule;
import com.oa.workflow.approver.app.rules.SubsidiaryGmRule;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.ApproverRule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 解析规则注册表（{@code enums.md} §3 的 <b>9 条</b>规则码 → 实现）。
 *
 * <p>存在意义：
 * <ol>
 *   <li>发起解析只按 {@code flow_node.approver_rule} 取实现，**没有任何 if/else 分支散落在服务里**；</li>
 *   <li>「规则清单」接口（{@code GET /approver-rules}）直接由注册表产出，
 *       每条的 {@code source()} 给出**出处章节**，便于审计对照文档；</li>
 *   <li>缺实现的规则码会在这里显式暴露（而不是静默解析成空候选人）。</li>
 * </ol>
 *
 * <p><b>兼容别名</b>：normify 基线的规则目录接口用的是节点式短名
 * （{@code dept-leader} / {@code company-exec} / {@code gm} / {@code group-exec} /
 * {@code finance-leader} / {@code collaborating-dept}）。这些**不是** {@code enums.md} §3 的规则码，
 * 本表只把它们当作**入参别名**映射到定稿规则码，避免前端按基线路径调用时 404。
 */
@Component
public class ApproverRuleRegistry {

    /** 基线短名（kebab） → 定稿规则码。 */
    private static final Map<String, String> ALIASES = aliases();

    private final Map<String, ApproverResolutionRule> rules = new LinkedHashMap<>();

    public ApproverRuleRegistry(OaProperties properties) {
        register(new DeptLeaderUpwardRule());
        register(new FinanceOwnerRule(properties.getScope().getFinanceDeptId(),
                properties.getScope().getFinanceDeptName()));
        register(new BranchLeaderRule());
        register(new SubsidiaryGmRule());
        register(new GroupLeaderRule());
        register(new ChairmanRule());
        register(new DesignatedRule());
        register(new InitiatorPickRule());
        register(new CollabDeptLeaderRule());
    }

    private static Map<String, String> aliases() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("dept-leader", ApproverRule.DEPT_LEADER_UPWARD.code());
        map.put("finance-leader", ApproverRule.FINANCE_OWNER.code());
        map.put("finance-owner", ApproverRule.FINANCE_OWNER.code());
        map.put("company-exec", ApproverRule.BRANCH_LEADER.code());
        map.put("gm", ApproverRule.SUBSIDIARY_GM.code());
        map.put("subsidiary-gm", ApproverRule.SUBSIDIARY_GM.code());
        map.put("group-exec", ApproverRule.GROUP_LEADER.code());
        map.put("group-leader", ApproverRule.GROUP_LEADER.code());
        map.put("chairman", ApproverRule.CHAIRMAN.code());
        map.put("designated", ApproverRule.DESIGNATED.code());
        map.put("initiator-pick", ApproverRule.INITIATOR_PICK.code());
        map.put("collaborating-dept", ApproverRule.COLLAB_DEPT_LEADER.code());
        map.put("collab-dept-leader", ApproverRule.COLLAB_DEPT_LEADER.code());
        // 注意：**不提供** department-leader / department 别名 ——
        // 它们是 enums.md §14 的废弃值，必须走「未知规则 → 空候选人 + 纠错文案」的拦截路径，
        // 否则设计器里残留的旧值会被静默解析成合法规则（内控风险）。
        return Map.copyOf(map);
    }

    private void register(ApproverResolutionRule rule) {
        rules.put(rule.code(), rule);
    }

    /** 按规则码取实现（支持基线短名别名；未知码返回空）。 */
    public Optional<ApproverResolutionRule> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        ApproverResolutionRule rule = rules.get(normalized);
        if (rule != null) {
            return Optional.of(rule);
        }
        String aliased = ALIASES.get(normalized.replace('_', '-'));
        return aliased == null ? Optional.empty() : Optional.ofNullable(rules.get(aliased));
    }

    /** 全部规则（按注册顺序 = enums.md §3 的行序）。 */
    public List<ApproverResolutionRule> all() {
        return new ArrayList<>(rules.values());
    }

    /** 已注册的规则码集合。 */
    public List<String> codes() {
        return new ArrayList<>(rules.keySet());
    }
}
