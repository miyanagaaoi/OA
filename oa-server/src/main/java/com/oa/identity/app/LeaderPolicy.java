package com.oa.identity.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.identity.domain.IdentityEnums.Category;
import com.oa.identity.domain.IdentityEnums.LeaderType;
import com.oa.identity.domain.IdentityEnums.OrgType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 组织负责人规则 —— <b>纯函数</b>（不依赖 Spring / DB）。
 *
 * <h2>口径（不得偏离）</h2>
 * <ul>
 *   <li><b>正职唯一</b>：同一 {@code (org_id, category)} 下 {@code leader_type = primary} 至多 1 人；
 *       副职不限。库约束为 {@code uk_org_leader (org_id, user_id, leader_type, category)}，
 *       业务口径见 import-spec §4.4 {@code E-LEAD-004} 与 T-09 定稿
 *       「以 {@code org_path + business_line} 为分组键，每组至多 1 条正职」；</li>
 *   <li><b>{@code category} 即业务线</b>（事项类别五值），{@code NULL} 表示「普通负责人」；
 *       「业务线绑定（category 非空）」与「普通负责人（category 为空）」**互不冲突**——
 *       它们是不同的分组键，可以各自拥有一个正职；</li>
 *   <li><b>业务线只能绑在集团层</b>：import-spec §4.4 {@code E-LEAD-008}
 *       「{@code business_line} 仅允许填在 {@code org_type=集团} 的节点上」；</li>
 *   <li>负责人**不得为离职人员**（import-spec §4.4 {@code E-LEAD-006}），由应用服务用
 *       {@code sys_user.status} 判定（本类只做纯逻辑，不查库）。</li>
 * </ul>
 */
public final class LeaderPolicy {

    /** 已存在的负责人绑定快照（{@code sys_org_leader} 最小投影）。 */
    public record Binding(Long id, Long orgId, Long userId, LeaderType leaderType, Category category) {

        /** 分组键：{@code (orgId, category)}，{@code category} 为 {@code null} 时是独立的「普通负责人」组。 */
        public String groupKey() {
            return orgId + "|" + (category == null ? "" : category.code());
        }

        public boolean primary() {
            return leaderType == LeaderType.PRIMARY;
        }
    }

    private LeaderPolicy() {
    }

    /**
     * 校验「新增/变更后的负责人绑定」是否满足正职唯一。
     *
     * @param existing  该组织（或全量）现有绑定，可为 {@code null}
     * @param candidate 待写入的绑定（{@code id} 为 {@code null} 表示新增）
     * @param orgName   组织名（错误文案用）
     */
    public static void assertPrimaryUnique(List<Binding> existing, Binding candidate, String orgName) {
        if (candidate == null || !candidate.primary()) {
            return;
        }
        Binding current = primaryOf(existing, candidate.orgId(), candidate.category()).orElse(null);
        if (current == null) {
            return;
        }
        if (Objects.equals(current.id(), candidate.id())) {
            // 同一行改自身（如改 sort/duty_title）不算冲突
            return;
        }
        if (Objects.equals(current.userId(), candidate.userId())) {
            return;
        }
        String scope = candidate.category() == null
                ? "（普通负责人）"
                : "（业务线：" + candidate.category().label() + "）";
        throw new BizException(ErrorCode.CONFLICT,
                "该组织" + (orgName == null ? "" : "「" + orgName + "」") + scope
                        + "已设置正职负责人，同一组织同一业务线至多 1 名正职（import-spec E-LEAD-004）");
    }

    /** 取某组织某业务线（{@code null} = 普通负责人组）的正职。 */
    public static Optional<Binding> primaryOf(List<Binding> bindings, Long orgId, Category category) {
        if (bindings == null || orgId == null) {
            return Optional.empty();
        }
        for (Binding binding : bindings) {
            if (binding == null || !binding.primary()) {
                continue;
            }
            if (!orgId.equals(binding.orgId())) {
                continue;
            }
            if (!Objects.equals(category, binding.category())) {
                continue;
            }
            return Optional.of(binding);
        }
        return Optional.empty();
    }

    /** 全量自洽性校验：同一 {@code (orgId, category)} 分组内不得出现 2 条正职。 */
    public static void assertPrimaryGrouping(List<Binding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        List<String> seen = new ArrayList<>();
        for (Binding binding : bindings) {
            if (binding == null || !binding.primary()) {
                continue;
            }
            String key = binding.groupKey();
            if (seen.contains(key)) {
                throw new BizException(ErrorCode.CONFLICT,
                        "同一组织同一业务线存在多个正职负责人（orgId=" + binding.orgId()
                                + ", category=" + (binding.category() == null ? "空" : binding.category().code()) + "）");
            }
            seen.add(key);
        }
    }

    /** 业务线（{@code category}）只能绑在集团层节点（import-spec E-LEAD-008）。 */
    public static void assertCategoryAllowed(OrgType orgType, Category category) {
        if (category == null) {
            return;
        }
        if (orgType != OrgType.GROUP) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "分管业务线只能绑定在集团层节点上（import-spec E-LEAD-008），当前组织类型为「"
                            + (orgType == null ? "未知" : orgType.label()) + "」");
        }
    }

    /** 排序：{@code sort_no} 升序，其次正职优先，再次 id 升序。 */
    public static List<Binding> sorted(List<Binding> bindings) {
        List<Binding> result = new ArrayList<>();
        if (bindings != null) {
            result.addAll(bindings);
        }
        result.sort((left, right) -> {
            if (left.primary() != right.primary()) {
                return left.primary() ? -1 : 1;
            }
            return Long.compare(left.id() == null ? 0 : left.id(), right.id() == null ? 0 : right.id());
        });
        return result;
    }
}
