package com.oa.form.document;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 单据类型 → 专属规则的注册表（**唯一分发点**）。
 *
 * <p>为什么要注册表而不是 {@code switch}：四类单据的规则分属四个包
 * （{@code oa.form.matter} / {@code oa.form.fund} / {@code oa.form.contract} / {@code oa.form.seal}），
 * 用注册表可以让每类规则各自成 Bean（Spring 注入 {@code List<FormTypeRules>}），
 * 新增单据类型时不必改这个类 —— 与 {@code doc/dev-plan-v0.3.md} §5「2b.3 四类单据」的模块划分一致。
 */
@Service
public class FormRuleRegistry {

    private final Map<String, FormTypeRules> rules = new LinkedHashMap<>();

    public FormRuleRegistry(List<FormTypeRules> beans) {
        if (beans != null) {
            for (FormTypeRules rule : beans) {
                String key = rule.formType() == null ? "" : rule.formType().trim().toLowerCase(Locale.ROOT);
                if (!key.isEmpty()) {
                    rules.put(key, rule);
                }
            }
        }
    }

    /** 取某单据类型的规则；未注册即 400（**不静默放行**：漏注册等于专属规则全丢）。 */
    public FormTypeRules require(String formType) {
        String key = formType == null ? "" : formType.trim().toLowerCase(Locale.ROOT);
        FormTypeRules rule = rules.get(key);
        if (rule == null) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    String.format("单据类型「%s」没有注册专属表单规则，已拒绝（防止专属校验被静默跳过）", formType))
                    .withDetail("formType", formType)
                    .withDetail("registered", rules.keySet());
        }
        return rule;
    }

    /** 已注册的单据类型（自检/披露）。 */
    public java.util.Set<String> registered() {
        return java.util.Collections.unmodifiableSet(rules.keySet());
    }
}
