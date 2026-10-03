package com.oa.workflow.definition.app;

/**
 * 一条流程定义校验问题（发布前 dry-run 报告的最小单元）。
 *
 * @param rule    校验规则 id（见 {@link PrePublishChecker#RULES}）
 * @param subject 问题主体（如「节点 2 finance_review」或「模板 matter v2」）
 * @param message 给流程管理员看的中文说明（必须能指导修正）
 */
public record DefinitionProblem(String rule, String subject, String message) {

    public static DefinitionProblem of(String rule, String subject, String message) {
        return new DefinitionProblem(rule, subject, message);
    }

    /** 供写接口的 400 文案使用：{@code subject：message}。 */
    public String describe() {
        if (subject == null || subject.isBlank()) {
            return message;
        }
        return subject + "：" + message;
    }
}
