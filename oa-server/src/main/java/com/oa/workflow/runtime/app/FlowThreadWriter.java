package com.oa.workflow.runtime.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.Candidate;
import com.oa.workflow.runtime.domain.RuntimeEnums.ThreadAction;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import com.oa.workflow.runtime.infra.row.FlowThreadRow;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审批轨迹写入器（{@code sys_thread}，doc/data-model.md §6.5）—— 2a.4 的**唯一**轨迹落点。
 *
 * <h2>纪律</h2>
 * <ol>
 *   <li><b>只追加</b>：本类没有任何 UPDATE/DELETE 路径（轨迹 ≥10 年不可篡改，enums.md §9）；</li>
 *   <li><b>只能落 16 个定稿值</b>：{@link ThreadAction} 是唯一值域（旧值 {@code addsign} /
 *       {@code return_node} / {@code archive} 一律无法经本类写入）；</li>
 *   <li><b>动作名必填</b>：{@code action} 为空即 500 级配置错误（宁可失败也不留一条看不懂的轨迹）；</li>
 *   <li><b>快照姓名与职务</b>：{@code actor_name} / {@code actor_position} 存**当时**的值，
 *       之后改名/调岗不影响历史轨迹；</li>
 *   <li>{@code seq} 由 {@code MAX(seq)+1} 取（同一实例的并发动作由实例行的行锁串行化，
 *       调用方必须在事务内调用本类）。</li>
 * </ol>
 */
@Component
public class FlowThreadWriter {

    private final FlowRuntimeMapper runtimeMapper;
    private final ApproverDirectory directory;

    public FlowThreadWriter(FlowRuntimeMapper runtimeMapper, ApproverDirectory directory) {
        this.runtimeMapper = runtimeMapper;
        this.directory = directory;
    }

    /**
     * 追加一条轨迹。
     *
     * @param instanceId     所属实例
     * @param nodeInstanceId 相关节点实例（实例级动作传 {@code null}）
     * @param actor          操作人（可为 {@code null}：系统动作，如补件超时催办）
     * @param action         轨迹动作（16 值之一）
     * @param opinion        意见/说明（可空）
     */
    @Transactional
    public FlowThreadRow append(Long instanceId, Long nodeInstanceId, CurrentUser actor,
                                ThreadAction action, String opinion) {
        if (instanceId == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "轨迹写入缺少 instanceId");
        }
        if (action == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "轨迹写入缺少 action（sys_thread.action 必填）");
        }
        FlowThreadRow row = new FlowThreadRow();
        row.setInstanceId(instanceId);
        row.setNodeInstanceId(nodeInstanceId);
        row.setSeq(nextSeq(instanceId));
        row.setActorId(actor == null ? null : actor.id());
        row.setActorName(actor == null ? "系统" : actor.name());
        row.setActorPosition(actor == null ? null : positionOf(actor));
        row.setAction(action.code());
        row.setOpinion(opinion);
        runtimeMapper.insertThread(row);
        return row;
    }

    /** 当前登录人视角的便捷写法。 */
    @Transactional
    public FlowThreadRow appendAsCurrentUser(Long instanceId, Long nodeInstanceId, ThreadAction action,
                                             String opinion) {
        DataScopeContext context = DataScopeContext.current();
        CurrentUser principal = context == null ? null : context.getPrincipal();
        return append(instanceId, nodeInstanceId, principal, action, opinion);
    }

    /** 下一个轨迹序号（实例内 1..N）。 */
    public Integer nextSeq(Long instanceId) {
        Integer next = runtimeMapper.nextThreadSeq(instanceId);
        return next == null ? 1 : next;
    }

    /** 操作人职务快照（取不到则 {@code null}，不阻断轨迹写入）。 */
    private String positionOf(CurrentUser actor) {
        try {
            Optional<Candidate> candidate = directory.user(actor.id());
            return candidate.map(Candidate::position).orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
