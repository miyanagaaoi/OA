package com.oa.form.template.validate;

/**
 * {@code org}「组织选择」字段的**范围**端口（{@code rules[orgScope]}）。
 *
 * <h2>真源</h2>
 * <ul>
 *   <li>{@code doc/templates.md} §2.3 规则表：{@code orgScope}｜org｜
 *       {@code value}：{@code initiator_company_subtree} 等｜「组织选择范围限制」；</li>
 *   <li>{@code doc/forms.md} §2 事项单字段表 {@code cost_bearer} 行：类型 {@code org}、
 *       校验「**限本公司及以下节点**」、默认值「发起人所属公司」；</li>
 *   <li>{@code doc/templates.md} §2.4 的模板 JSON 实例（{@code cost_bearer.rules[1]}
 *       = {@code {"type":"orgScope","value":"initiator_company_subtree","message":"费用承担主体限本公司及以下节点"}}）。</li>
 * </ul>
 *
 * <h2>为什么是端口</h2>
 * <p>与 {@link PickerValueChecker} 同一理由：{@link FormPayloadValidator} 保持**纯函数**可穷举单测
 * （单测传一个 lambda 即可），生产实现见 {@code com.oa.form.infra.FormOrgScopeChecker}，
 * 复用既有的组织目录端口 {@code com.oa.workflow.approver.app.ApproverDirectory}（不另造一份组织树查询）。
 *
 * <p>未装配（{@code null}）时**跳过并记 WARN**，与 {@code unique} / {@code pickerValue} 的缺省口径一致。
 *
 * <h2>查询口径（数据域纪律）</h2>
 * <p>判定发生在**系统口径**下（{@code DataScopeContext.system()}，由
 * {@code JdbcApproverDirectory} 统一包裹）：这是**内部判定**，只回答「这个组织节点在不在发起人
 * 公司子树内」这个布尔事实，<b>不对外暴露任何域外内容</b>（不回显名称/负责人/上级），
 * 因此不构成读取旁路 —— 与 {@code FormPickerDirectoryChecker} 的已定稿理由逐字一致。
 * <p>调用人的**对外可见性不受影响**：列表 / 详情 / 抄送一律仍走 {@code @dataScope} 织入，
 * 本端口既不读业务单据，也不参与任何可见性判定。
 *
 * <h2>「发起人所属公司」的口径</h2>
 * <p>由调用方（{@code FormDataService}）给出，两条取数路径：
 * <ul>
 *   <li>有实例 → {@code flow_instance.initiator_company_id}（发起时固化的快照，
 *       因此事后调岗/改组织**不会**改变在途单据的范围判定，AC-09 同源）；</li>
 *   <li>无实例（建草稿 / 干跑）→ 当前登录人 {@code sys_user.company_id}
 *       （与 {@code ApproverPrecheckService#buildContext} 的 {@code company_id} 同源）。</li>
 * </ul>
 * <p>{@code companyId} 为 {@code null}（发起人无公司，例如未挂公司的集团层账号）时，
 * 本端口一律返回 {@code false} —— <b>fail-closed</b>：范围无法判定时判「越界」，
 * 不静默放行（见交付说明「待决策」：文档未写集团层账号的「本公司」口径）。
 */
public interface OrgScopeChecker {

    /**
     * @param orgId     待判组织节点 id（{@code sys_org.id} 的十进制文本，前端选择器的取值形态）
     * @param companyId 发起人所属公司 id；{@code null} 表示「无法确定发起人所属公司」
     * @return {@code true} 表示该节点落在 {@code companyId} 的**子树内（含公司自身）**且节点启用
     */
    boolean withinInitiatorCompanySubtree(String orgId, Long companyId);
}
