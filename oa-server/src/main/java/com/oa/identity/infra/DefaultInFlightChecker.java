package com.oa.identity.infra;

import com.oa.common.scope.DataScopeContext;
import com.oa.identity.app.InFlightChecker;
import com.oa.identity.infra.row.InFlightItemRow;
import com.oa.identity.infra.row.PendingTaskRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 在途/待办检查的**真实实现**（阶段 2a.3 把桩变成真实查询）。
 *
 * <h2>判定口径（与 {@link InFlightChecker} 的接口注释、PRD §5.5、AC-11/AC-12 逐条一致）</h2>
 * <ol>
 *   <li><b>在途单据</b> = {@code flow_instance.status = 'approving'}
 *       （待补件期间主状态仍是 {@code approving}，见 doc/enums.md §4，因此天然包含在内）；</li>
 *   <li><b>组织口径</b>（{@link #checkOrgSubtree}）：按 {@code sys_org.path} 前缀匹配
 *       {@code flow_instance.initiator_org_path}（发起人组织快照）<b>或</b>
 *       {@code current_dept_id} 落在子树内 —— 覆盖「发起人在该子树」与「单据当前流转到该子树」两种在途形态
 *       （依据 doc/import-spec.md §8.2「该组织节点（含其整棵子树）下的在途单据数」）；</li>
 *   <li><b>人员口径</b>（{@link #checkUser}）：{@code flow_task.assignee_id = ? AND status = 'pending'}
 *       的任务数（doc/import-spec.md §8.1「名下未处理待办」）<b>加上</b>
 *       {@code flow_node_instance.status='active'} 且 {@code approver_ids_json} 含本人的**在途单据数**
 *       （2a.4 追加口径：依次签的后续候选人 / 已固化在快照但尚未产生任务的候选人；
 *       该追加口径记为 {@code inFlightInstances}，**不与待办数重复计数**）；</li>
 *   <li><b>明细口径</b>（{@link #inFlightItems} / {@link #orgInFlightItems}）与数量口径
 *       **共用同一套过滤条件**（同一 Mapper 的相邻语句），不另写第二套判定逻辑。</li>
 * </ol>
 *
 * <h2>为什么一律使用系统口径</h2>
 * <p>这是「离职/停用前的影响面检查」：不能因为调用人（管理员）自己的数据域而漏算在途单据，
 * 否则补偿控制失效（PRD §5.5 明确「否则快照会导致单据永久卡死」）。
 * 因此所有查询都包在 {@link DataScopeContext#system()} 里，
 * 由 {@link InFlightQueryMapper} 的 {@code @dataScope} 标记退化为 {@code 1=1}。
 * <b>这不是数据域旁路</b>：这些结果只用于「是否阻断操作」的提示与判定，不产生任何读取暴露
 * （出参只有单号、类型、节点名、状态，不含单据正文）。
 *
 * <p>系统管理员可用 {@code force=true} + 必填理由覆盖阻断（AC-52 双留痕），
 * 该授权在控制器由 {@code ForceReasonPolicy} 把关，本类**不做**角色判断
 * （口径与 {@code InFlightGuard} 一致：一处判定，避免分叉）。
 */
@Component
public class DefaultInFlightChecker implements InFlightChecker {

    private static final Logger log = LoggerFactory.getLogger(DefaultInFlightChecker.class);

    /** 文案与影响清单里最多列举的单号数（与 {@code InFlightGuard} 的展示上限对齐）。 */
    private static final int MAX_BIZ_NO = 20;

    /** 影响清单最多返回的行数（防止一次拉爆响应体）。 */
    private static final int MAX_ITEMS = 200;

    private final InFlightQueryMapper mapper;

    public DefaultInFlightChecker(InFlightQueryMapper mapper) {
        this.mapper = mapper;
    }

    // ================================================================ 数量口径

    @Override
    public InFlightSummary checkOrgSubtree(String orgPathPrefix) {
        String prefix = normalizePrefix(orgPathPrefix);
        if (prefix == null) {
            log.warn("在途检查缺少组织路径前缀，按「无在途」返回（调用方应传 sys_org.path）");
            return InFlightSummary.none();
        }
        return systemScope(() -> {
            int count = mapper.countOrgInFlight(prefix);
            if (count == 0) {
                return InFlightSummary.none();
            }
            List<String> bizNos = mapper.selectOrgInFlightBizNos(prefix, MAX_BIZ_NO);
            if (log.isDebugEnabled()) {
                log.debug("在途检查（真实查询）：orgPathPrefix={} 在途单据={} 单号={}", prefix, count, bizNos);
            }
            return new InFlightSummary(count, 0, bizNos);
        });
    }

    @Override
    public InFlightSummary checkUser(Long userId) {
        if (userId == null) {
            return InFlightSummary.none();
        }
        return systemScope(() -> {
            int pending = mapper.countUserPendingTasks(userId);
            // 2a.4 追加口径：本人在**活动节点候选**内（依次签的后续候选人、快照候选人尚未产生任务）
            int candidates = mapper.countUserCandidateNodes(userId);
            if (pending == 0 && candidates == 0) {
                return InFlightSummary.none();
            }
            List<String> bizNos = new ArrayList<>(mapper.selectUserPendingBizNos(userId, MAX_BIZ_NO));
            if (candidates > 0) {
                for (String bizNo : mapper.selectUserCandidateBizNos(userId, MAX_BIZ_NO)) {
                    if (!bizNos.contains(bizNo)) {
                        bizNos.add(bizNo);
                    }
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("待办检查（真实查询）：userId={} 待办={} 活动节点候选单据={} 单号={}",
                        userId, pending, candidates, bizNos);
            }
            // 候选单据计入 inFlightInstances（**不与 pendingTasks 重复计数**，避免 total 翻倍）
            return new InFlightSummary(candidates, pending, bizNos);
        });
    }

    // ================================================================ 清单口径

    @Override
    public List<PendingTask> pendingTasksOf(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return systemScope(() -> {
            List<PendingTask> tasks = new ArrayList<>();
            for (PendingTaskRow row : mapper.selectUserPendingTasks(userId, MAX_ITEMS)) {
                tasks.add(new PendingTask(row.getTaskId(), row.getBizNo(), row.getNodeName(), row.getCreatedAt()));
            }
            return tasks;
        });
    }

    @Override
    public List<InFlightItem> inFlightItems(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return systemScope(() -> {
            List<InFlightItem> items = new ArrayList<>(toItems(mapper.selectUserInFlightItems(userId, MAX_ITEMS)));
            // 2a.4：候选但尚无待办的在途单据也要出现在影响清单里（否则「拦截了却看不到要处理什么」）
            Set<Long> seen = new java.util.LinkedHashSet<>();
            for (InFlightItem item : items) {
                seen.add(item.instanceId());
            }
            for (InFlightItem item : toItems(mapper.selectUserCandidateInFlightItems(userId, MAX_ITEMS))) {
                if (item.instanceId() != null && seen.add(item.instanceId())) {
                    items.add(item);
                }
            }
            return items;
        });
    }

    @Override
    public List<InFlightItem> orgInFlightItems(String orgPathPrefix) {
        String prefix = normalizePrefix(orgPathPrefix);
        if (prefix == null) {
            return List.of();
        }
        return systemScope(() -> toItems(mapper.selectOrgInFlightItems(prefix, MAX_ITEMS)));
    }

    /**
     * 批量待办数（列表页的 {@code pendingTaskCount}）：**一条 SQL** 而非逐行回退。
     *
     * <p>已从接口默认实现（逐条 {@code checkUser}）覆盖为分组计数，消除 N+1。
     */
    @Override
    public Map<Long, Integer> pendingTaskCounts(Collection<Long> userIds) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<>();
        for (Long id : userIds) {
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        return systemScope(() -> {
            Map<Long, Integer> counts = new LinkedHashMap<>();
            for (PendingTaskRow row : mapper.selectPendingTaskCounts(ids)) {
                if (row != null && row.getAssigneeId() != null) {
                    counts.put(row.getAssigneeId(), row.getTotal() == null ? 0 : row.getTotal());
                }
            }
            // 未命中的用户补 0（调用方约定「未命中按 0 处理」，这里显式给出更省事）
            for (Long id : ids) {
                result.put(id, counts.getOrDefault(id, 0));
            }
            return result;
        });
    }

    // ================================================================ 内部

    /** 组织路径前缀归一：{@code /1/12} → {@code /1/12/}；空值返回 {@code null}。 */
    static String normalizePrefix(String orgPathPrefix) {
        if (orgPathPrefix == null || orgPathPrefix.isBlank()) {
            return null;
        }
        String value = orgPathPrefix.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return value;
    }

    private static List<InFlightItem> toItems(List<InFlightItemRow> rows) {
        List<InFlightItem> items = new ArrayList<>();
        if (rows == null) {
            return items;
        }
        for (InFlightItemRow row : rows) {
            if (row == null) {
                continue;
            }
            items.add(new InFlightItem(row.getInstanceId(), row.getBizNo(), row.getFormType(),
                    row.getNodeName(), row.getInitiatorName(), row.getCurrentNodeName(), row.getStatus()));
        }
        return items;
    }

    /** 影响面查询一律走系统口径（详见类注释「为什么一律使用系统口径」）。 */
    private <T> T systemScope(Supplier<T> action) {
        DataScopeContext previous = DataScopeContext.current();
        DataScopeContext.set(DataScopeContext.system());
        try {
            return action.get();
        } finally {
            if (previous == null) {
                DataScopeContext.clear();
            } else {
                DataScopeContext.set(previous);
            }
        }
    }
}
