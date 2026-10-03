package com.oa.workflow.approver.app;

/**
 * 单条审批人解析规则（**每条规则一个可单测的单元**）。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>{@link #code()} 必须逐字等于 {@code doc/enums.md} §3 的规则码；</li>
 *   <li>{@link #resolve} **不得抛异常表达「无候选人」**：返回
 *       {@link RuleOutcome#empty} 并给出缺什么配置，由
 *       {@code ApproverPrecheckService} 统一转成拦截文案（AC-11/AC-19）；</li>
 *   <li>实现**不做数据域判断**：数据源口径由 {@link ApproverDirectory} 的实现负责
 *       （发起时按系统口径解析，见该接口的类注释）；</li>
 *   <li>实现必须是**纯函数**（只读 {@code directory} 与入参），因此可注入内存目录穷举单测。</li>
 * </ul>
 */
public interface ApproverResolutionRule {

    /** 规则码（{@code enums.md} §3）。 */
    String code();

    /** 规则中文名（用于「规则清单」接口与拦截文案）。 */
    String label();

    /** 解析依据（出处章节，用于「规则清单」接口的可追溯性）。 */
    String source();

    /**
     * 解析候选人。
     *
     * @param node      目标节点配置（{@code approver_param} 挂在节点上）
     * @param request   发起上下文
     * @param directory 身份目录端口
     */
    RuleOutcome resolve(NodeConfig node, RuleRequest request, ApproverDirectory directory);
}
