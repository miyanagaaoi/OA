package com.oa.workflow.definition.domain;

import com.oa.workflow.definition.domain.FlowGateEnums.DeadlineType;
import com.oa.workflow.definition.domain.FlowGateEnums.TimeoutAction;
import com.oa.workflow.definition.domain.FlowGateEnums.WithdrawWindow;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 流程模板（表 {@code flow_template}，doc/data-model.md §4.1）。
 *
 * <p><b>版本累积、不覆盖历史</b>（templates.md V-01）：唯一键 {@code (code, version)}，
 * 每次发布 {@code version + 1}；已发布版本**只读**，改配置必须开新草稿版本。
 * 在途实例通过 {@code flow_instance.template_id + template_version} 锁定执行依据（V-02）。
 *
 * <p>Q6/Q7 闸门配置（裁定新增的 5 列）+ 撤回窗口口径（2026-10-04 裁定新增的第 6 列）见
 * {@link FlowGatePolicy}（键位与语义表见 doc/templates.md §1.7 / §1.8）。
 */
public class FlowTemplate {

    private static final Logger log = LoggerFactory.getLogger(FlowTemplate.class);

    private Long id;
    private String code;
    private String name;
    private String formType;
    private Integer version;
    private String status;
    /** 主干节点数（发布时按实际节点行数回写）。 */
    private Integer nodeCount;
    /** 表单字段定义 JSON（驱动渲染；见 doc/forms.md）。 */
    private String formSchemaJson;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    // ---- Q6 / Q7 闸门配置 + 撤回窗口（列定义见 doc/data-model.md §4.1，随 Flyway V1 建列）----
    private Integer maxReturnCount;
    private Integer maxSupplementCount;
    private Integer supplementDeadlineDays;
    private String supplementDeadlineType;
    private String onSupplementTimeout;
    /** 撤回窗口口径：{@code until_finance_approved}（默认口径）/ {@code until_finance_started}（严格口径）；{@code NULL} = 取默认。 */
    private String withdrawWindow;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(Integer nodeCount) {
        this.nodeCount = nodeCount;
    }

    public String getFormSchemaJson() {
        return formSchemaJson;
    }

    public void setFormSchemaJson(String formSchemaJson) {
        this.formSchemaJson = formSchemaJson;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Integer getMaxReturnCount() {
        return maxReturnCount;
    }

    public void setMaxReturnCount(Integer maxReturnCount) {
        this.maxReturnCount = maxReturnCount;
    }

    public Integer getMaxSupplementCount() {
        return maxSupplementCount;
    }

    public void setMaxSupplementCount(Integer maxSupplementCount) {
        this.maxSupplementCount = maxSupplementCount;
    }

    public Integer getSupplementDeadlineDays() {
        return supplementDeadlineDays;
    }

    public void setSupplementDeadlineDays(Integer supplementDeadlineDays) {
        this.supplementDeadlineDays = supplementDeadlineDays;
    }

    public String getSupplementDeadlineType() {
        return supplementDeadlineType;
    }

    public void setSupplementDeadlineType(String supplementDeadlineType) {
        this.supplementDeadlineType = supplementDeadlineType;
    }

    public String getOnSupplementTimeout() {
        return onSupplementTimeout;
    }

    public void setOnSupplementTimeout(String onSupplementTimeout) {
        this.onSupplementTimeout = onSupplementTimeout;
    }

    public String getWithdrawWindow() {
        return withdrawWindow;
    }

    public void setWithdrawWindow(String withdrawWindow) {
        this.withdrawWindow = withdrawWindow;
    }

    /** Q6/Q7 闸门配置 + 撤回窗口视图（枚举列解析失败时按 {@code null}，由校验环节报错）。 */
    public FlowGatePolicy gatePolicy() {
        return new FlowGatePolicy(
                maxReturnCount,
                maxSupplementCount,
                supplementDeadlineDays,
                supplementDeadlineType == null ? null : DeadlineType.of(supplementDeadlineType).orElse(null),
                onSupplementTimeout == null ? null : TimeoutAction.of(onSupplementTimeout).orElse(null),
                withdrawWindowEnum(withdrawWindow));
    }

    /**
     * 撤回窗口列 → 枚举，**非法值必须留下 WARN（含原始值）**。
     *
     * <p>背景（2026-10-04 微修）：该列原先写作 {@code WithdrawWindow.of(withdrawWindow).orElse(null)}，
     * 于是一个绕过 DB {@code chk_flow_template_gates} 硬改进去的非法字符串会被**静默**映射成
     * {@code NULL} —— 而 {@code NULL} 的语义是「取默认口径 {@code until_finance_approved}」
     * （doc/templates.md §1.8）。二者在行为上都是 fail-open（退化为默认口径，这是**可接受**的），
     * 但「静默」不可接受：管理员手改过的数据会在无人知情的情况下按另一种口径裁决撤回。
     * 因此这里只补**可观测性**，取值语义与降级行为一字未改。
     */
    private WithdrawWindow withdrawWindowEnum(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        java.util.Optional<WithdrawWindow> parsed = WithdrawWindow.of(raw);
        if (parsed.isPresent()) {
            return parsed.get();
        }
        log.warn("flow_template.withdraw_window 取值非法（原始值=\"{}\"），已按 NULL 处理并退化到默认口径 {}；"
                        + "合法取值为 {} / {}。出现本日志说明 DB 约束 chk_flow_template_gates 被绕过"
                        + "（或数据被直接改写），请核对模板 id={} 的配置来源。",
                raw, WithdrawWindow.defaultWindow().code(),
                WithdrawWindow.UNTIL_FINANCE_APPROVED.code(), WithdrawWindow.UNTIL_FINANCE_STARTED.code(),
                id);
        return null;
    }

    /** 写入闸门配置（{@code null} 表示「不限/不设时限/取默认撤回窗口」，枚举按 {@code code()} 落库）。 */
    public void applyGatePolicy(FlowGatePolicy policy) {
        FlowGatePolicy effective = policy == null ? FlowGatePolicy.unlimited() : policy.normalized();
        this.maxReturnCount = effective.effectiveMaxReturnCount();
        this.maxSupplementCount = effective.effectiveMaxSupplementCount();
        this.supplementDeadlineDays = effective.effectiveSupplementDeadlineDays();
        DeadlineType type = effective.effectiveDeadlineType();
        this.supplementDeadlineType = type == null ? null : type.code();
        this.onSupplementTimeout = effective.effectiveTimeoutAction().code();
        // 撤回窗口：null 落 NULL（= 取默认 until_finance_approved），不把默认值回填成显式值
        WithdrawWindow window = effective.withdrawWindow();
        this.withdrawWindow = window == null ? null : window.code();
    }

    /** 模板状态枚举（非法值返回 {@code null}）。 */
    public FlowDefinitionEnums.TemplateStatus statusEnum() {
        return FlowDefinitionEnums.TemplateStatus.of(status).orElse(null);
    }

    public boolean isDraft() {
        return statusEnum() == FlowDefinitionEnums.TemplateStatus.DRAFT;
    }
}
