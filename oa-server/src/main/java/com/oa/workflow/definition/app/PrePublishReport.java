package com.oa.workflow.definition.app;

import java.util.List;

/**
 * 发布前 dry-run 校验报告（{@code POST /flow-designs/{template_id}/pre-publish-check} 的出参）。
 *
 * <p>依据 {@code oa.workflow.designer.validation}：「任一项不通过即阻止发布并给出具体原因」。
 * 报告面向**流程管理员**：每条结论都必须能指导修正（哪个节点、哪个字段、期望值是什么）。
 *
 * @param templateId  模板 id
 * @param code        单据类型码
 * @param version     版本号
 * @param status      模板状态（发布前应为 {@code draft}）
 * @param passed      是否通过（{@code problems} 为空）
 * @param generatedAt 生成时间（{@code yyyy-MM-dd HH:mm:ss}）
 * @param checks      逐规则结论（含通过项，便于前端展示检查清单）
 * @param problems    未通过项的可读说明（空 = 可发布）
 * @param warnings    提示项（不阻止发布，如「已开启自由跳转」）
 */
public record PrePublishReport(
        Long templateId,
        String code,
        Integer version,
        String status,
        boolean passed,
        String generatedAt,
        List<Check> checks,
        List<String> problems,
        List<String> warnings
) {

    /**
     * 单条规则结论。
     *
     * @param rule    规则 id（见 {@link PrePublishChecker#RULES}）
     * @param title   规则标题（中文）
     * @param status  {@code pass} / {@code fail} / {@code warn}
     * @param details 该规则命中的说明清单（{@code pass} 时为空）
     */
    public record Check(String rule, String title, String status, List<String> details) {

        public static Check pass(String rule, String title) {
            return new Check(rule, title, "pass", List.of());
        }

        public static Check of(String rule, String title, List<String> details) {
            return new Check(rule, title, details == null || details.isEmpty() ? "pass" : "fail",
                    details == null ? List.of() : List.copyOf(details));
        }

        public static Check warn(String rule, String title, List<String> details) {
            return new Check(rule, title, "warn", details == null ? List.of() : List.copyOf(details));
        }
    }
}
