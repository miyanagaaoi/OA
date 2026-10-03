package com.oa.form.infra;

import com.oa.common.scope.DataScopeContext;
import com.oa.form.template.validate.UniqueValueChecker;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code rules[type=unique]} 的生产实现（{@link UniqueValueChecker}）。
 *
 * <h2>为什么用系统口径</h2>
 * <p>唯一性是**全局约束**：{@code doc/forms.md} §3 的 {@code contract_ref} 行要求
 * 「存在时必须为**已通过**的单据号」——「存在」这件事与调用人的数据域无关。
 * 若在调用人数据域下判，域外已通过的合同单会被判成「不存在」，用户看到的是
 * 「关联合同单号必须是已存在且已通过的合同审批单号」这种**无法自查**的错误。
 * 因此这里显式切到 {@code DataScopeContext.system()} 后执行
 * {@link FormDataMapper#countApprovedInstanceByBizNo}（语句本身保留 {@code @dataScope} 标记，
 * 这样既拿到全库口径、又不必往豁免清单里加条目 —— 与 {@code FlowInstanceService#countByBizNo} 逐字同法）。
 *
 * <h2>只暴露存在性，不暴露内容</h2>
 * <p>本实现只回答「有没有」这一个布尔问题，**不返回任何行数据**，
 * 因此不构成数据域读取旁路（同 {@code SysUserMapper#countByAccountSystem} 的窄豁免理由）。
 */
@Component
public class FormUniqueChecker implements UniqueValueChecker {

    private static final Logger log = LoggerFactory.getLogger(FormUniqueChecker.class);

    /** 模板里 {@code contract_ref} 的 {@code scope} 文本（doc/templates.md §2.3 示例口径）。 */
    public static final String SCOPE_INSTANCE_BIZ_NO = "flow_instance.biz_no";

    private final FormDataMapper formDataMapper;

    public FormUniqueChecker(FormDataMapper formDataMapper) {
        this.formDataMapper = formDataMapper;
    }

    @Override
    public boolean exists(String scope, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (!SCOPE_INSTANCE_BIZ_NO.equals(scope)) {
            log.warn("未知的 unique scope「{}」（已知：{}），按「不通过」处理以免静默放行",
                    scope, SCOPE_INSTANCE_BIZ_NO);
            return false;
        }
        int count = systemScope(() -> formDataMapper.countApprovedInstanceByBizNo(value.trim()));
        return count > 0;
    }

    private <T> T systemScope(Supplier<T> action) {
        DataScopeContext previous = DataScopeContext.current();
        DataScopeContext.set(DataScopeContext.system());
        try {
            return action.get();
        } finally {
            if (previous == null) {
                DataScopeContext.clear();
            } else {
                DataScopeContext.set(previous);
            }
        }
    }
}
