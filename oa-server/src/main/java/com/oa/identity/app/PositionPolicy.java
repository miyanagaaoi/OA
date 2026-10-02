package com.oa.identity.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 岗位任职（一人多岗）规则 —— <b>纯函数</b>（不依赖 Spring / DB）。
 *
 * <h2>口径（不得偏离）</h2>
 * <ul>
 *   <li>一人可挂多个组织节点（{@code sys_user_position} 唯一键 {@code uk_user_org (user_id, org_id)}）；</li>
 *   <li><b>主岗每人至多 1 个</b>：{@code is_primary = 1} 在同一 {@code user_id} 下唯一
 *       （import-spec §4.5 {@code E-POS-005}「每人最多一个 {@code is_primary=是}」）；</li>
 *   <li><b>设置主岗时自动把其旧主岗置 0</b>（同一事务），本类用
 *       {@link #demotionsFor} 一次性算出需要降级的岗位 id，避免逐条循环里再查库；</li>
 *   <li>主岗的 {@code position} 回填 {@code sys_user.position}、主岗的 {@code org_id} 回填
 *       {@code sys_user.org_id}（import-spec §2.2 第 ④ 步、T-12 定稿「主岗应与
 *       {@code user.csv.dept_path} 一致」）。</li>
 * </ul>
 */
public final class PositionPolicy {

    /** 已存在的岗位任职快照（{@code sys_user_position} 最小投影）。 */
    public record Assignment(Long id, Long userId, Long orgId, boolean primary, String position) {
    }

    private PositionPolicy() {
    }

    /**
     * 设置（新增或修改为）主岗时，需要**同事务降级**的既有岗位 id 列表。
     *
     * @param existing         该用户既有岗位，可为 {@code null}
     * @param userId           目标用户
     * @param targetPositionId 本次要置为主岗的那条记录 id；新增时为 {@code null}
     * @return 需要 {@code is_primary = 0} 的岗位 id（不含目标记录自身）
     */
    public static List<Long> demotionsFor(List<Assignment> existing, Long userId, Long targetPositionId) {
        List<Long> result = new ArrayList<>();
        if (existing == null || userId == null) {
            return result;
        }
        for (Assignment assignment : existing) {
            if (assignment == null || !assignment.primary()) {
                continue;
            }
            if (!userId.equals(assignment.userId())) {
                continue;
            }
            if (assignment.id() != null && assignment.id().equals(targetPositionId)) {
                continue;
            }
            if (assignment.id() != null) {
                result.add(assignment.id());
            }
        }
        return result;
    }

    /** 同一用户的主岗（用于回填 {@code sys_user.org_id} / {@code position}）。 */
    public static Optional<Assignment> primaryOf(List<Assignment> assignments, Long userId) {
        if (assignments == null || userId == null) {
            return Optional.empty();
        }
        for (Assignment assignment : assignments) {
            if (assignment != null && assignment.primary() && userId.equals(assignment.userId())) {
                return Optional.of(assignment);
            }
        }
        return Optional.empty();
    }

    /** 自洽性校验：同一用户不得有 2 个主岗（导入/存量数据体检也复用本方法）。 */
    public static void assertSinglePrimary(List<Assignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }
        List<Long> users = new ArrayList<>();
        List<Long> duplicated = new ArrayList<>();
        for (Assignment assignment : assignments) {
            if (assignment == null || !assignment.primary() || assignment.userId() == null) {
                continue;
            }
            if (users.contains(assignment.userId()) && !duplicated.contains(assignment.userId())) {
                duplicated.add(assignment.userId());
            }
            users.add(assignment.userId());
        }
        if (!duplicated.isEmpty()) {
            throw new BizException(ErrorCode.CONFLICT,
                    "同一用户存在多个主岗（is_primary=1），违反 import-spec E-POS-005：" + duplicated);
        }
    }

    /**
     * 解除一条岗位任职后的主岗兜底：若被解除的是主岗且该用户仍有其它岗位，
     * 则把剩余岗位中 id 最小的一条提升为主岗，维持「有岗位则有且仅有 1 个主岗」。
     *
     * <p><b>说明</b>：该兜底**不是** import-spec 明文规则（文档只约束「至多 1 个主岗」），
     * 属于实现期补充口径，已记入交付说明的待确认项。
     *
     * @return 需要提升为主岗的岗位 id；无需提升时为空
     */
    public static Optional<Long> promotionAfterRemoval(List<Assignment> existing, Long userId, Long removedId) {
        if (existing == null || userId == null) {
            return Optional.empty();
        }
        Assignment removed = null;
        List<Assignment> remaining = new ArrayList<>();
        for (Assignment assignment : existing) {
            if (assignment == null || !userId.equals(assignment.userId())) {
                continue;
            }
            if (assignment.id() != null && assignment.id().equals(removedId)) {
                removed = assignment;
                continue;
            }
            remaining.add(assignment);
        }
        if (removed == null || !removed.primary() || remaining.isEmpty()) {
            return Optional.empty();
        }
        Assignment candidate = null;
        for (Assignment assignment : remaining) {
            if (assignment.primary()) {
                // 已有主岗（理论上不会发生），无需提升
                return Optional.empty();
            }
            if (candidate == null || compareId(assignment, candidate) < 0) {
                candidate = assignment;
            }
        }
        return candidate == null || candidate.id() == null ? Optional.empty() : Optional.of(candidate.id());
    }

    private static int compareId(Assignment left, Assignment right) {
        long leftId = left.id() == null ? Long.MAX_VALUE : left.id();
        long rightId = right.id() == null ? Long.MAX_VALUE : right.id();
        return Long.compare(leftId, rightId);
    }
}
