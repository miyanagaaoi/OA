package com.oa.form.template.validate;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 表单校验报告 —— <b>一次给全所有失败项</b>（不是只报第一个）。
 *
 * <p>两个消费面：
 * <ol>
 *   <li><b>HTTP 错误响应</b>：{@link #fail()} 把所有失败项拼进
 *       {@link ErrorCode#FORM_VALIDATION_FAILED} 的 message，**并**把 {@link #issueViews()}
 *       经 {@code BizException#withPublicDetail("errors", …)} 放进响应体的
 *       {@code details.errors[]}（2026-10-04 追加；排查用的 {@code withDetail} 仍只进日志）；</li>
 *   <li><b>干跑接口</b>：{@code POST /api/v1/forms/{form_type}/validate} 直接把
 *       {@link #view()} 作为 200 出参返回，前端可逐字段标红。
 *       两个面的失败项**同源**（同一个 {@link #issueViews()}），不会出现两套形状。</li>
 * </ol>
 */
public final class FormValidationReport {

    private final List<FieldIssue> issues;

    private FormValidationReport(List<FieldIssue> issues) {
        this.issues = Collections.unmodifiableList(issues);
    }

    public static FormValidationReport ok() {
        return new FormValidationReport(new ArrayList<>());
    }

    public static FormValidationReport of(List<FieldIssue> issues) {
        return new FormValidationReport(issues == null ? new ArrayList<>() : new ArrayList<>(issues));
    }

    /** 累积式构造（校验器内部用）；**同一 (字段, 规则, 文案) 只保留一条**。 */
    public static final class Collector {

        private final List<FieldIssue> issues = new ArrayList<>();
        private final java.util.Set<String> seen = new java.util.LinkedHashSet<>();

        /**
         * 追加一条失败项（去重 + 同规则内取更完整的那条文案）。
         *
         * <p>两条去重口径：
         * <ol>
         *   <li>「字段码 + 规则名 + 文案」完全相同 → 只留一条（纵深防御下 schema 与业务规则
         *       会命中同一字段同一文案，例如资金单金额既有 {@code rules[amountRange]} 又有
         *       {@code FundFormRules#validateAmount} 的业务兜底）；</li>
         *   <li>同一字段同一规则下，若新文案被已有文案**包含**（或反之）→ 只留更完整的那条：
         *       校验器为了「能定位到原因」会把具体原因（如「禁止使用浮点数提交」）附加在模板文案之后，
         *       而业务兜底只给模板文案 —— 两者是同一件事，不该在响应里出现两行。</li>
         * </ol>
         * 文案互不包含（例如多选项 A 与 B 各自非法且文案不同）时**不会被合掉**。
         */
        public void add(String fieldCode, String label, String rule, String message) {
            String key = fieldCode + "\u0000" + rule + "\u0000" + message;
            if (seen.contains(key)) {
                return;
            }
            for (int i = 0; i < issues.size(); i++) {
                FieldIssue existing = issues.get(i);
                if (!java.util.Objects.equals(existing.fieldCode(), fieldCode)
                        || !java.util.Objects.equals(existing.rule(), rule)) {
                    continue;
                }
                if (existing.message() != null && existing.message().contains(message)) {
                    return;
                }
                if (existing.message() != null && message != null && message.contains(existing.message())) {
                    seen.remove(existing.fieldCode() + "\u0000" + existing.rule() + "\u0000" + existing.message());
                    seen.add(key);
                    issues.set(i, new FieldIssue(fieldCode, label, rule, message));
                    return;
                }
            }
            seen.add(key);
            issues.add(new FieldIssue(fieldCode, label, rule, message));
        }

        public void add(FieldIssue issue) {
            if (issue != null) {
                add(issue.fieldCode(), issue.label(), issue.rule(), issue.message());
            }
        }

        public void addAll(FormValidationReport report) {
            if (report != null) {
                for (FieldIssue issue : report.issues()) {
                    add(issue);
                }
            }
        }

        public boolean isEmpty() {
            return issues.isEmpty();
        }

        public int size() {
            return issues.size();
        }

        public List<FieldIssue> issues() {
            return issues;
        }

        public FormValidationReport build() {
            return FormValidationReport.of(issues);
        }
    }

    public static Collector collector() {
        return new Collector();
    }

    public List<FieldIssue> issues() {
        return issues;
    }

    public boolean passed() {
        return issues.isEmpty();
    }

    /** 是否存在针对某字段的失败项。 */
    public boolean hasIssueOn(String fieldCode) {
        return issues.stream().anyMatch(issue -> issue.fieldCode() != null && issue.fieldCode().equals(fieldCode));
    }

    /** 某字段的全部失败项文案。 */
    public List<String> messagesOf(String fieldCode) {
        return issues.stream()
                .filter(issue -> issue.fieldCode() != null && issue.fieldCode().equals(fieldCode))
                .map(FieldIssue::message)
                .collect(Collectors.toList());
    }

    /** 出参视图（干跑接口用）。 */
    public Map<String, Object> view() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("passed", passed());
        view.put("issueCount", issues.size());
        view.put("issues", issueViews());
        return view;
    }

    /**
     * 失败项的**结构化清单**（唯一生产者）。
     *
     * <p>两个消费面**同源**（避免两套形状各自漂移）：
     * <ol>
     *   <li>干跑接口 {@code POST /api/v1/forms/{formType}/validate} → {@code report.issues[]}；</li>
     *   <li>校验失败的 HTTP 错误响应（40011）→ {@code details.errors[]}
     *       （{@link #fail()} 经 {@code BizException#withPublicDetail} 放进响应体）。</li>
     * </ol>
     * <p>键只有四个：{@code field}（字段码）、{@code label}（标签）、{@code rule}（规则名）、
     * {@code message}（可读文案）—— **不含**任何内部实现信息（异常类名、SQL、模板内部 id），
     * 因此可以安全地直接下发（AC-41）。
     */
    public List<Map<String, Object>> issueViews() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (FieldIssue issue : issues) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", issue.fieldCode());
            item.put("label", issue.label());
            item.put("rule", issue.rule());
            item.put("message", issue.message());
            items.add(item);
        }
        return items;
    }

    /**
     * 不通过即抛 400 {@link ErrorCode#FORM_VALIDATION_FAILED}（message 含**全部**失败项）。
     *
     * <p><b>两处都写</b>（2026-10-04 起）：
     * <ul>
     *   <li>{@code withDetail("errors", …)} → 服务端日志（排查用，口径不变）；</li>
     *   <li>{@code withPublicDetail("errors", …)} → **HTTP 响应体的 {@code details.errors[]}**：
     *       前端原先只能从「字段码（标签）：原因」的 message 文本里**尽力还原**逐字段错误，
     *       现在拿到结构化明细（与干跑接口 {@code report.issues[]} 同一份数据）。</li>
     * </ul>
     * message 一字未改：既有解析 message 的调用方不受影响。
     */
    public void fail() {
        if (passed()) {
            return;
        }
        List<Map<String, Object>> views = issueViews();
        throw new BizException(ErrorCode.FORM_VALIDATION_FAILED,
                String.format("表单字段校验未通过（%d 项）：%s", issues.size(), summary()))
                .withDetail("errors", views)
                .withPublicDetail("errors", views);
    }

    /** 全部失败项的一行摘要（保持校验顺序）。 */
    public String summary() {
        return issues.stream().map(FieldIssue::describe).collect(Collectors.joining("；"));
    }
}
