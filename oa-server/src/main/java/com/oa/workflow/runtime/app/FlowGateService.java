package com.oa.workflow.runtime.app;

import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.definition.domain.FlowGateEnums.DeadlineType;
import com.oa.workflow.definition.domain.FlowGateEnums.WithdrawWindow;
import com.oa.workflow.definition.domain.FlowGatePolicy;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import com.oa.workflow.runtime.domain.GateCounterPolicy;
import com.oa.workflow.runtime.domain.SupplementDeadlinePolicy;
import com.oa.workflow.runtime.domain.SupplementDeadlinePolicy.Deadline;
import com.oa.workflow.runtime.domain.WithdrawWindowPolicy;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * <b>Q6 / Q7 的运行期读取与判定入口</b>（2a.4）。
 *
 * <h2>为什么单独一层</h2>
 * <p>Q6/Q7 的配置是**模板级**的（{@code flow_template} 的可空列，doc/templates.md §1.7；撤回窗口见 §1.8），
 * 但在途实例只认**发起时锁定的那一行模板**（{@code flow_instance.template_id}，REQ-FLOW-006 / AC-09）。
 * 因此「读配置」这件事只有一个正确落点：按实例锁定的 {@code template_id} 取回模板行，
 * 再交给纯策略 {@link GateCounterPolicy}（Q6）与 {@link SupplementDeadlinePolicy}（Q7）判定，
 * 撤回窗口交 {@link WithdrawWindowPolicy}。
 * 引擎与接口都从这里取，避免「有的地方reads 最新版本、有的地方 reads 锁定版本」的口径分叉。
 * 2026-10-04 新增的 {@link #withdrawWindowOf} 复用**同一条**取数路径（不另造一套）。
 *
 * <h2>读不到模板行怎么办</h2>
 * <p>{@code flow_instance.template_id} 有外键约束且不可为空，正常不会读不到；真读不到时按
 * {@link FlowGatePolicy#unlimited()}（不限次数、不设时限、仅提醒）处理并打 WARN —— 这是**不阻断**
 * 的降级：Q6 是「防空转」的闸门，不是审批的必要条件（对比 AC-11 空候选人拦截是硬闸门）。
 */
@Service
public class FlowGateService {

    private static final Logger log = LoggerFactory.getLogger(FlowGateService.class);

    private final FlowTemplateMapper templateMapper;

    public FlowGateService(FlowTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    /** 实例锁定的模板行（可能为空，见类注释）。 */
    public Optional<FlowTemplate> lockedTemplate(FlowInstanceRow instance) {
        if (instance == null || instance.getTemplateId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(templateMapper.selectTemplateById(instance.getTemplateId()));
    }

    /** 实例锁定的 Q6/Q7 配置（读不到 → {@link FlowGatePolicy#unlimited()}）。 */
    public FlowGatePolicy policyOf(FlowInstanceRow instance) {
        FlowTemplate template = lockedTemplate(instance).orElse(null);
        if (template == null) {
            log.warn("实例 {} 锁定模板 id={} 不存在，Q6/Q7 按「不限」降级",
                    instance == null ? null : instance.getId(),
                    instance == null ? null : instance.getTemplateId());
            return FlowGatePolicy.unlimited();
        }
        return template.gatePolicy();
    }

    // ================================================================ Q6

    /** 回退/流转预算（只读判定，不抛异常；出参回显用）。 */
    public GateCounterPolicy.Budget returnBudget(FlowInstanceRow instance) {
        return GateCounterPolicy.evaluateReturn(policyOf(instance).effectiveMaxReturnCount(),
                used(instance == null ? null : instance.getRoutingCount()));
    }

    /** 补件预算（只读判定）。 */
    public GateCounterPolicy.Budget supplementBudget(FlowInstanceRow instance) {
        return GateCounterPolicy.evaluateSupplement(policyOf(instance).effectiveMaxSupplementCount(),
                used(instance == null ? null : instance.getSupplementCount()));
    }

    /** 回退/流转预算：超限即抛 40908（文案含上限 / 已用 / 剩余 0 次）。 */
    public GateCounterPolicy.Budget assertReturnBudget(FlowInstanceRow instance, String actionLabel) {
        return GateCounterPolicy.assertReturnBudget(policyOf(instance).effectiveMaxReturnCount(),
                used(instance.getRoutingCount()), actionLabel);
    }

    /** 补件预算：超限即抛 40909。 */
    public GateCounterPolicy.Budget assertSupplementBudget(FlowInstanceRow instance) {
        return GateCounterPolicy.assertSupplementBudget(policyOf(instance).effectiveMaxSupplementCount(),
                used(instance.getSupplementCount()));
    }

    // ================================================================ Q7

    /**
     * 计算本次补件的**应完成时间**（Q7 就位；超时策略的**执行**属阶段 3）。
     *
     * @param base 请求时刻（生产传 {@code LocalDateTime.now()}）
     */
    public Deadline deadlineFor(FlowInstanceRow instance, LocalDateTime base) {
        FlowGatePolicy policy = policyOf(instance);
        return SupplementDeadlinePolicy.compute(base,
                policy.effectiveSupplementDeadlineDays(),
                policy.effectiveDeadlineType());
    }

    /** 时限口径披露（法定节假日表不存在 → 只跳周末，见 {@link SupplementDeadlinePolicy}）。 */
    public String calendarDisclosure() {
        return SupplementDeadlinePolicy.WEEKEND_ONLY.name();
    }

    /** 该实例的超时策略值（{@code notify} / {@code auto_pass} / {@code auto_return}）——**仅披露，不执行**。 */
    public String timeoutActionOf(FlowInstanceRow instance) {
        return policyOf(instance).effectiveTimeoutAction().code();
    }

    /** 时限口径（{@code calendar} / {@code working}）。 */
    public DeadlineType deadlineTypeOf(FlowInstanceRow instance) {
        return policyOf(instance).effectiveDeadlineType();
    }

    // ================================================================ 撤回窗口

    /**
     * 该实例**生效的撤回窗口口径**（2026-10-04 裁定新增；与 Q6/Q7 走**同一条取数路径**）。
     *
     * <p>口径只认**发起时锁定的模板版本**（{@code flow_instance.template_id}，REQ-FLOW-006 / AC-09）：
     * 读不到模板行、或该列为 {@code NULL} 时按 {@link WithdrawWindow#defaultWindow()}
     * （{@code until_finance_approved} = REQ-FLOW-009 口径 = 历史行为）处理并打 WARN。
     * **刻意不读当前 published 模板** —— 否则「管理员改模板 → 在途单据的撤回窗口跟着变」，
     * 直接违反 AC-09（已发起实例按发起时版本执行）。
     *
     * @return 永不为 {@code null}；消费点见
     *         {@code FlowEngineService#withdrawAllowed} → {@link WithdrawWindowPolicy}
     */
    public WithdrawWindow withdrawWindowOf(FlowInstanceRow instance) {
        FlowTemplate template = lockedTemplate(instance).orElse(null);
        if (template == null) {
            log.warn("实例 {} 锁定模板 id={} 不存在，撤回窗口按默认口径 {} 降级",
                    instance == null ? null : instance.getId(),
                    instance == null ? null : instance.getTemplateId(),
                    WithdrawWindow.defaultWindow().code());
            return WithdrawWindow.defaultWindow();
        }
        return template.gatePolicy().effectiveWithdrawWindow();
    }

    private static int used(Integer count) {
        return count == null ? 0 : count;
    }
}
