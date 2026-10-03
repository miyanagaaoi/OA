package com.oa.form.app;

import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import org.springframework.stereotype.Service;

/**
 * <b>提交前闸门</b>（2b.1 / 2b.3）：把「已落库的表单」按 {@code SUBMIT} 档再校验一次。
 *
 * <h2>为什么提交动作必须再过一次（真源原文）</h2>
 * <ul>
 *   <li>{@code doc/forms.md} §1.1「必填 | **不填能否提交**」——判据绑在「提交」这个动作上；</li>
 *   <li>{@code doc/forms.md} §3 资金单 {@code amount} 行：「{@code > 0}，见 1.5；**提交前必须通过**」，
 *       以及 §3 业务补充说明「金额为 0 或空时**禁止提交**（PRD 13.2）」；</li>
 *   <li>{@code doc/forms.md} §11.2「服务端二次校验：所有必填、长度、金额、日期校验必须在服务端执行」；</li>
 *   <li>{@code doc/prd-0.1.md} AC-28：越权写入（含「内部接口越权调用」）必须被拒。</li>
 * </ul>
 *
 * <h2>两层都在</h2>
 * <ul>
 *   <li><b>入口层</b>：{@code PUT /forms/instances/{id}/draft?mode=submit} 与
 *       {@code POST /forms/instances/{id}/validate}（干跑，能一次把全部不合格项给前端）；</li>
 *   <li><b>引擎层</b>：{@code FlowEngineService#submit} 在状态迁移**之前**调用本闸门 ——
 *       防的是「绕过表单接口直调提交」与「草稿期用宽松档存了一半就直接提交」。</li>
 * </ul>
 *
 * <p>独立成一个 Bean 而不是让引擎直接依赖 {@link FormDataService}：引擎只需要「通过 / 不通过」
 * 这一个布尔语义，接口面越小越不容易被误用（也不给后续工作包顺手在引擎里写表单数据的口子）。
 */
@Service
public class FormSubmitGate {

    private final FormDataService formDataService;

    public FormSubmitGate(FormDataService formDataService) {
        this.formDataService = formDataService;
    }

    /**
     * 断言该实例的表单满足提交口径，否则抛 400 {@code FORM_VALIDATION_FAILED}。
     *
     * <p>返回提示语（写审计与轨迹用），永不返回 {@code null}。
     */
    public String assertSubmittable(FlowInstanceRow instance) {
        formDataService.assertSubmitReady(instance);
        return "表单服务端二次校验通过（schema 驱动，SUBMIT 档）";
    }
}
