package com.oa.identity.app;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在途单据 / 待办检查端口（<b>可替换接口</b>）。
 *
 * <p>存在意义（施工要求第 3 条 + AC-11/AC-12）：组织停用与人员离职前必须确认
 * 「该节点子树下无在途单据」「该人名下无待处理待办」，否则按 {@code oa.identity.block-on-inflight}
 * 拒绝或仅告警。流程表（{@code flow_instance} / {@code flow_task}）在阶段 1 尚无实现，
 * 因此本能力先以端口形式隔离：
 *
 * <ul>
 *   <li>接口在本包（{@code com.oa.identity.app}），只暴露身份领域需要的最小语义；</li>
 *   <li><b>阶段 2a.3 起，实现已经是真实查询</b>：
 *       {@code com.oa.identity.infra.DefaultInFlightChecker} 走
 *       {@code InFlightQueryMapper}（带 {@code @dataScope} 标记）按
 *       {@code flow_instance.status='approving'} 与 {@code flow_task.status='pending'} 计数，
 *       并在 {@code DataScopeContext.system()} 下执行（影响面统计不能因调用人数据域漏算）；</li>
 *   <li>替换方式不变：新增一个 {@code @Primary} 实现即可，
 *       身份侧调用方（{@code OrgService} / {@code UserService}）无需改动。</li>
 * </ul>
 *
 * <p><b>一条端口、多处复用</b>（施工要求第 3 条「复用同一端口，不要新造第二套检查逻辑」）：
 * <ol>
 *   <li>数量口径：{@link #checkOrgSubtree} / {@link #checkUser} → 拦截判定（{@link InFlightGuard}）；</li>
 *   <li>清单口径：{@link #pendingTasksOf}（待办，工作交接用）、
 *       {@link #inFlightItems}（人员影响清单，{@code GET /users/{id}/in-flight-check}）、
 *       {@link #orgInFlightItems}（组织影响清单，{@code GET /orgs/{id}/in-flight-check}）；</li>
 *   <li>列表页口径：{@link #pendingTaskCounts}（批量，避免逐行 N+1）。</li>
 * </ol>
 *
 * <p><b>实现要点（阶段 2a.3 已落地）</b>：
 * 组织口径按 {@code sys_org.path} 前缀匹配 {@code flow_instance.initiator_org_path} /
 * {@code current_dept_id}，人员口径按 {@code flow_task.assignee_id = ? AND status = 'pending'}。
 * 这些查询属于**流程域受控表**（{@code flow_instance}/{@code flow_task} 已在
 * {@code oa.scope.tables} 登记），实现带 {@code /* @dataScope(...) *}{@code /} 标记，
 * 并显式使用 {@code DataScopeContext.system()}（后台口径）。
 * <p>{@code TODO(2a.4)}：人员口径追加「本人在活动节点候选内」
 * （{@code flow_node_instance.status='active'} 且 {@code approver_ids_json} 含本人）——
 * 该列的结构由 2a.4 运行时状态机定义，本工作包不猜测其形状。
 */
public interface InFlightChecker {

    /**
     * 组织停用前的在途检查（**含整棵子树**，按 {@code sys_org.path} 前缀匹配）。
     *
     * @param orgPathPrefix 形如 {@code /1/12/135/}
     */
    InFlightSummary checkOrgSubtree(String orgPathPrefix);

    /** 人员离职前的待办检查（名下 {@code flow_task.status='pending'} + 活动节点候选）。 */
    InFlightSummary checkUser(Long userId);

    /** 该人名下待办清单（工作交接用；缺省实现返回空清单）。 */
    List<PendingTask> pendingTasksOf(Long userId);

    /**
     * 该人名下的在途/待办**明细**（人员影响清单，{@code GET /users/{id}/in-flight-check}）。
     *
     * <p>比 {@link #pendingTasksOf} 多出「单据类型 / 发起人 / 当前节点 / 状态」等列
     * （import-spec §7.2 影响清单八列的口径）。流程表未落地时返回**空清单**，
     * 调用方据此回 0 条而不是 null。
     */
    List<InFlightItem> inFlightItems(Long userId);

    /**
     * 该组织**整棵子树**下的在途单据明细（组织影响清单，{@code GET /orgs/{id}/in-flight-check}）。
     *
     * @param orgPathPrefix 形如 {@code /1/12/135/}
     */
    List<InFlightItem> orgInFlightItems(String orgPathPrefix);

    /**
     * 批量待办数（{@code GET /users} 列表页的 {@code pendingTaskCount}，避免逐行 N+1）。
     *
     * <p>缺省实现逐条回退到 {@link #checkUser}：桩实现零成本，真实现应覆盖为**一条 SQL**
     * （{@code flow_task} 按 assignee 分组计数）。
     *
     * @return userId → 待办数；未命中的用户由调用方按 0 处理
     */
    default Map<Long, Integer> pendingTaskCounts(Collection<Long> userIds) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (userIds == null) {
            return result;
        }
        for (Long userId : userIds) {
            if (userId != null) {
                result.put(userId, checkUser(userId).pendingTasks());
            }
        }
        return result;
    }

    /**
     * 在途/待办汇总。
     *
     * @param inFlightInstances 在途单据数（{@code flow_instance.status='approving'} 等未完结态）
     * @param pendingTasks      待处理待办数（{@code flow_task.status='pending'}）
     * @param bizNos            涉及单号（用于提示文案与影响清单；实现方应限制条数）
     */
    record InFlightSummary(int inFlightInstances, int pendingTasks, List<String> bizNos) {

        public InFlightSummary {
            bizNos = bizNos == null ? List.of() : List.copyOf(bizNos);
        }

        /** 恒为 0 的缺省值（流程表未落地时的口径）。 */
        public static InFlightSummary none() {
            return new InFlightSummary(0, 0, List.of());
        }

        public boolean hasInFlight() {
            return inFlightInstances > 0 || pendingTasks > 0;
        }

        /** 影响总数（提示文案中的「N 条」）。 */
        public int total() {
            return inFlightInstances + pendingTasks;
        }
    }

    /**
     * 待办条目（工作交接清单）。
     *
     * @param taskId    任务 id
     * @param bizNo     单号
     * @param nodeName  节点名
     * @param createdAt 生成时间
     */
    record PendingTask(Long taskId, String bizNo, String nodeName, String createdAt) {
    }

    /**
     * 在途/待办**明细条目**（影响清单行，人员口径与组织口径共用）。
     *
     * <p>字段与 import-spec §7.2「受影响在途单据清单」对齐；组织口径不涉及单条任务时
     * {@code currentNodeName}/{@code status} 可为 {@code null}
     * （出参侧 {@code spring.jackson.default-property-inclusion=non_null} 会自动省略空字段）。
     *
     * @param instanceId      在途实例 id（{@code flow_instance.id}；出参按字符串下发）
     * @param bizNo           单号（{@code flow_instance.biz_no}）
     * @param formType        单据类型（{@code flow_instance.form_type}）
     * @param nodeName        所在/待处理节点名
     * @param initiatorName   发起人姓名
     * @param currentNodeName 当前节点名
     * @param status          单据/任务状态
     */
    record InFlightItem(Long instanceId, String bizNo, String formType, String nodeName,
                        String initiatorName, String currentNodeName, String status) {
    }
}

