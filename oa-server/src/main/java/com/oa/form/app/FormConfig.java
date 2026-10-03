package com.oa.form.app;

import com.oa.form.dict.FormDictService;
import com.oa.form.template.validate.FormPayloadValidator;
import com.oa.form.template.validate.OrgScopeChecker;
import com.oa.form.template.validate.PickerValueChecker;
import com.oa.form.template.validate.UniqueValueChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 表单域的装配（2b.1）。
 *
 * <p>{@link FormPayloadValidator} 刻意做成**纯函数类**（无 {@code @Service}、无 Spring 注解）：
 * 它可以在单测里直接 {@code new} 出来穷举校验规则，不必拉起容器。生产装配落在这里，
 * 依赖恰好四个端口：字典（{@link FormDictService}）、唯一性（{@link UniqueValueChecker}）、
 * 人员/组织选择器存在性（{@link PickerValueChecker}）、组织选择范围（{@link OrgScopeChecker}，
 * {@code rules[orgScope] = initiator_company_subtree}）。
 */
@Configuration
public class FormConfig {

    /** schema 驱动的服务端二次校验器（2b.1）。 */
    @Bean
    public FormPayloadValidator formPayloadValidator(FormDictService dictService,
                                                     UniqueValueChecker uniqueValueChecker,
                                                     PickerValueChecker pickerValueChecker,
                                                     OrgScopeChecker orgScopeChecker) {
        return new FormPayloadValidator(dictService, uniqueValueChecker, pickerValueChecker, orgScopeChecker);
    }
}
