package com.oa.form.app;

import com.oa.form.dict.FormDictService;
import com.oa.form.template.validate.FormPayloadValidator;
import com.oa.form.template.validate.UniqueValueChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 表单域的装配（2b.1）。
 *
 * <p>{@link FormPayloadValidator} 刻意做成**纯函数类**（无 {@code @Service}、无 Spring 注解）：
 * 它可以在单测里直接 {@code new} 出来穷举校验规则，不必拉起容器。生产装配落在这里，
 * 依赖恰好两个端口：字典（{@link FormDictService}）与唯一性（{@link UniqueValueChecker}）。
 */
@Configuration
public class FormConfig {

    /** schema 驱动的服务端二次校验器（2b.1）。 */
    @Bean
    public FormPayloadValidator formPayloadValidator(FormDictService dictService,
                                                     UniqueValueChecker uniqueValueChecker) {
        return new FormPayloadValidator(dictService, uniqueValueChecker);
    }
}
