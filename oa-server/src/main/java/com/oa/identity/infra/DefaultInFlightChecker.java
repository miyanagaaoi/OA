package com.oa.identity.infra;

import com.oa.identity.app.InFlightChecker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 在途/待办检查的**缺省实现**：恒返回 0 / 空清单（流程表尚未落地的阶段）。
 *
 * <h2>接入点（阶段 2 待接入，务必按此实现）</h2>
 * <ol>
 *   <li><b>组织口径</b> {@code checkOrgSubtree(orgPathPrefix)}：按 {@code sys_org.path} 前缀匹配
 *       该节点**整棵子树**下的在途单据——
 *       {@code flow_instance.status = 'approving'}（必要时含 {@code sub_status = 'pending_supplement'}）
 *       且（{@code initiator_org_path LIKE :prefix} 或 {@code current_dept_id IN (子树 id)})；
 *       返回单号列表（{@code biz_no}）。依据 doc/import-spec.md §8.2「该组织节点（含其整棵子树，
 *       按 sys_org.path 前缀匹配）下的在途单据数」。</li>
 *   <li><b>人员口径</b> {@code checkUser(userId)}：{@code flow_task.assignee_id = ? AND status = 'pending'}
 *       的任务数，加上 {@code flow_node_instance.status = 'active'} 且候选人为该用户的活动节点数
 *       （doc/import-spec.md §8.1）。</li>
 *   <li><b>待办清单</b> {@code pendingTasksOf(userId)}：join {@code flow_instance} 取 {@code biz_no}
 *       与节点名，供「工作交接」清单使用。</li>
 *   <li><b>影响清单明细</b> {@code inFlightItems(userId)} / {@code orgInFlightItems(orgPathPrefix)}：
 *       同上一并 join {@code flow_instance} / {@code flow_task} / {@code form_template}，
 *       取「单据类型 formType / 发起人 initiatorName / 当前节点 currentNodeName / 状态 status」
 *       （import-spec §7.2 影响清单八列），供
 *       {@code GET /users/{id}/in-flight-check} 与 {@code GET /orgs/{id}/in-flight-check}；
 *       <b>与数量口径共用同一套过滤条件</b>，不得另写第二套判定逻辑。</li>
 * </ol>
 *
 * <p><b>实现时必须遵守的框架约束</b>（否则会被 fail-closed 拦截或越权）：
 * <ul>
 *   <li>{@code flow_instance} / {@code flow_task} 已在 {@code oa.scope.tables} 登记为受控表，
 *       新写的 SELECT **必须**带 {@code /* @dataScope(table=..., alias=...) *}{@code /} 标记；</li>
 *   <li>这是「离职/停用前的影响面检查」，语义上是**系统口径**的统计查询（不能因为调用人的数据域
 *       而漏算在途单据），因此实现方应显式使用 {@code DataScopeContext.system()} 包裹该查询，
 *       并在注释里写明「此处按系统口径统计，仅用于影响面提示与拦截，不用于授权判定」；</li>
 *   <li>替换方式：新增一个 {@code @Primary} 的 {@link InFlightChecker} 实现即可，
 *       身份侧调用方（{@code OrgService} / {@code UserService}）无需改动。</li>
 * </ul>
 */
@Component
public class DefaultInFlightChecker implements InFlightChecker {

    private static final Logger log = LoggerFactory.getLogger(DefaultInFlightChecker.class);

    @Override
    public InFlightSummary checkOrgSubtree(String orgPathPrefix) {
        if (log.isDebugEnabled()) {
            log.debug("在途检查（缺省桩，恒返回 0）：orgPathPrefix={}；接入点见 DefaultInFlightChecker 类注释", orgPathPrefix);
        }
        return InFlightSummary.none();
    }

    @Override
    public InFlightSummary checkUser(Long userId) {
        if (log.isDebugEnabled()) {
            log.debug("待办检查（缺省桩，恒返回 0）：userId={}；接入点见 DefaultInFlightChecker 类注释", userId);
        }
        return InFlightSummary.none();
    }

    @Override
    public List<PendingTask> pendingTasksOf(Long userId) {
        return List.of();
    }

    /**
     * 人员影响清单：流程表未落地 → **空清单**（调用方据此回 0 条，而不是 null）。
     *
     * <p>接入点：{@code flow_task t JOIN flow_instance i ON i.id = t.instance_id
     * LEFT JOIN form_template ft ON ft.id = i.template_id}，
     * 过滤 {@code t.assignee_id = ? AND t.status = 'pending'}，
     * 取 {@code i.id, i.biz_no, ft.form_type, t.node_name, i.initiator_name, t.node_name, t.status}；
     * 语句必须带 {@code @dataScope} 标记（受控表 {@code flow_task}/{@code flow_instance}）。
     */
    @Override
    public List<InFlightItem> inFlightItems(Long userId) {
        if (log.isDebugEnabled()) {
            log.debug("人员影响清单（缺省桩，返回空清单）：userId={}；接入点见本类类注释第 4 条", userId);
        }
        return List.of();
    }

    /**
     * 组织影响清单：流程表未落地 → **空清单**。
     *
     * <p>接入点：{@code flow_instance i} 按 {@code i.initiator_org_path LIKE :prefix}
     * （或子树 id 集合）过滤 {@code i.status = 'approving'}，
     * 取 {@code i.id, i.biz_no, ft.form_type, i.initiator_name, i.current_node_name}；
     * 与 {@link #checkOrgSubtree(String)} 共用同一套过滤条件。
     */
    @Override
    public List<InFlightItem> orgInFlightItems(String orgPathPrefix) {
        if (log.isDebugEnabled()) {
            log.debug("组织影响清单（缺省桩，返回空清单）：orgPathPrefix={}；接入点见本类类注释第 4 条", orgPathPrefix);
        }
        return List.of();
    }
}
