package com.oa.form.template.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.form.FormSchemaFixtures;
import com.oa.form.dict.FormDictService;
import com.oa.form.dict.InMemoryDictMapper;
import com.oa.form.template.schema.FormSchema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>2b.1 服务端二次校验</b>（doc/forms.md §11.2 / doc/templates.md §2.5）——
 * 「客户端会放行、服务端必须拒绝」的用例集。
 *
 * <p>校验对象是**真源种子**里的四条 schema（{@link FormSchemaFixtures}），
 * 因此断言里的字段码、长度上限、字典取值都与生产一致。
 */
class FormPayloadValidatorTest {

    private FormPayloadValidator validator;
    private FormSchema matter;
    private FormSchema fund;
    private FormSchema contract;
    private FormSchema seal;

    /** 固定的「今天」，避免用例随系统日期漂移。 */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

    @BeforeEach
    void setUp() {
        validator = FormPayloadValidator.offline(new FormDictService(new InMemoryDictMapper()));
        Map<String, FormSchema> schemas = FormSchemaFixtures.schemas();
        matter = schemas.get("matter");
        fund = schemas.get("fund");
        contract = schemas.get("contract");
        seal = schemas.get("seal");
    }

    private FormValidationReport validate(FormSchema schema, Map<String, Object> payload, ValidationMode mode) {
        return validator.validate(schema, payload, mode, TODAY, null);
    }

    // ================================================================ 夹带字段

    @Test
    @DisplayName("夹带字段：未在 schema 内登记的键**一律拒绝**（不是忽略）")
    void unknownFieldIsRejected() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "正常标题");
        payload.put("evil_field", "偷偷加的东西");
        payload.put("approver_snapshot_json", "{}");

        FormValidationReport report = validate(matter, payload, ValidationMode.DRAFT);
        assertThat(report.passed()).isFalse();
        assertThat(report.issues()).extracting(FieldIssue::fieldCode)
                .containsExactlyInAnyOrder("evil_field", "approver_snapshot_json");
        assertThat(report.issues()).allMatch(issue -> "unknownField".equals(issue.rule()));
        assertThat(report.summary()).contains("禁止夹带未登记字段");
    }

    // ================================================================ 必填 / 条件必填

    @Test
    @DisplayName("必填：提交档拒绝空与全空格；草稿档不强制定填（forms.md §1.3 的空值口径）")
    void requiredHonoursBlankRule() {
        Map<String, Object> blank = new LinkedHashMap<>();
        blank.put("title", "     ");
        FormValidationReport submit = validate(matter, blank, ValidationMode.SUBMIT);
        assertThat(submit.messagesOf("title")).containsExactly("请填写事项标题");

        assertThat(validate(matter, blank, ValidationMode.DRAFT).passed())
                .as("草稿允许不完整（必填的口径是「不填能否**提交**」，forms.md §1.1）")
                .isTrue();
    }

    @Test
    @DisplayName("条件必填：事项单 involve_cost=true 时 amount/cost_bearer 必填")
    void conditionalRequired() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "涉及费用的事项");
        payload.put("category", "business");
        payload.put("description", "这是一段足够长的事项描述");
        payload.put("involve_cost", true);
        FormValidationReport report = validate(matter, payload, ValidationMode.SUBMIT);
        assertThat(report.messagesOf("amount")).isNotEmpty();
        assertThat(report.messagesOf("cost_bearer")).isNotEmpty();
        assertThat(report.issues()).extracting(FieldIssue::rule).contains("conditionalRequired");
    }

    @Test
    @DisplayName("条件必填：合同单 contract_type=other 时 contract_type_other 必填")
    void contractOtherTypeNote() {
        Map<String, Object> payload = baseContract();
        payload.put("contract_type", "other");
        FormValidationReport report = validate(contract, payload, ValidationMode.SUBMIT);
        assertThat(report.messagesOf("contract_type_other")).isNotEmpty();
    }

    // ================================================================ 长度

    @Test
    @DisplayName("超长文本：title 61 字符被拒（长度上限逐字段来自 schema 的 maxLength/rules）")
    void maxLengthIsEnforced() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "标".repeat(61));
        FormValidationReport report = validate(matter, payload, ValidationMode.DRAFT);
        assertThat(report.messagesOf("title")).containsExactly("事项标题不能超过 60 个字符");
        assertThat(report.issues()).extracting(FieldIssue::rule).contains("maxLength");

        payload.put("title", "标".repeat(60));
        assertThat(validate(matter, payload, ValidationMode.DRAFT).hasIssueOn("title")).isFalse();
    }

    @Test
    @DisplayName("长度下限：事项描述 <10 字符被拒（minLength）")
    void minLengthIsEnforced() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("description", "太短");
        FormValidationReport report = validate(matter, payload, ValidationMode.DRAFT);
        assertThat(report.messagesOf("description")).containsExactly("事项描述至少 10 个字符");
    }

    // ================================================================ 枚举 / 字典

    @Test
    @DisplayName("非法枚举：事项类别不在字典内被拒；旧 code operate 已作废")
    void illegalEnumIsRejected() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", "operate");
        FormValidationReport report = validate(matter, payload, ValidationMode.DRAFT);
        assertThat(report.messagesOf("category")).containsExactly("事项类别取值非法");
        assertThat(report.issues()).extracting(FieldIssue::rule).contains("inDict");

        payload.put("category", "invest");
        assertThat(validate(matter, payload, ValidationMode.DRAFT).passed()).isTrue();
    }

    @Test
    @DisplayName("多选枚举：其他会审部门逐项校验 + 上限 10 项")
    void multiSelectEnumAndLimit() {
        Map<String, Object> payload = baseContract();
        payload.put("other_review_depts", List.of("econ_dev", "not_a_dept"));
        FormValidationReport report = validate(contract, payload, ValidationMode.DRAFT);
        assertThat(report.messagesOf("other_review_depts"))
                .as("模板 rules[inDict] 自带 message → 用它（doc/templates.md §2.3「所有规则项都支持 message」）")
                .containsExactly("其他会审部门取值非法");
        assertThat(report.issues()).extracting(FieldIssue::rule).contains("inDict");

        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            tooMany.add("econ_dev");
        }
        payload.put("other_review_depts", tooMany);
        assertThat(validate(contract, payload, ValidationMode.DRAFT).issues())
                .extracting(FieldIssue::rule).contains("pickerLimit");
    }

    // ================================================================ 金额

    @Test
    @DisplayName("金额：浮点字面量 / 0 / 三位小数 / 字符串负数 一律被拒（禁浮点）")
    void amountRules() {
        assertThat(validate(fund, fundPayload(100.12d), ValidationMode.DRAFT).messagesOf("amount"))
                .anyMatch(message -> message.contains("禁止使用浮点数"));

        assertThat(validate(fund, fundPayload("0"), ValidationMode.DRAFT).messagesOf("amount"))
                .containsExactly("金额必须大于 0 且最多两位小数");

        assertThat(validate(fund, fundPayload("100.123"), ValidationMode.DRAFT).messagesOf("amount"))
                .containsExactly("金额必须大于 0 且最多两位小数");

        assertThat(validate(fund, fundPayload("-100"), ValidationMode.DRAFT).hasIssueOn("amount")).isTrue();

        assertThat(validate(fund, fundPayload("100.12"), ValidationMode.DRAFT).hasIssueOn("amount")).isFalse();
    }

    @Test
    @DisplayName("金额为空：草稿档放行，提交档拒绝（forms.md §3「金额为 0 或空时禁止提交」）")
    void amountRequiredOnlyOnSubmit() {
        Map<String, Object> payload = fundPayload(null);
        assertThat(validate(fund, payload, ValidationMode.DRAFT).hasIssueOn("amount")).isFalse();
        assertThat(validate(fund, payload, ValidationMode.SUBMIT).messagesOf("amount")).isNotEmpty();
    }

    // ================================================================ 日期

    @Test
    @DisplayName("日期：早于今天被拒；格式非法被拒；区间结束早于开始被拒")
    void dateRules() {
        Map<String, Object> payload = fundPayload("100.00");
        payload.put("pay_date", "2020-01-01");
        FormValidationReport report = validate(fund, payload, ValidationMode.DRAFT);
        assertThat(report.messagesOf("pay_date")).containsExactly("计划支付日期不能早于今天");

        payload.put("pay_date", "2026/07/20");
        assertThat(validate(fund, payload, ValidationMode.DRAFT).messagesOf("pay_date"))
                .anyMatch(message -> message.contains("YYYY-MM-DD"));

        Map<String, Object> contractPayload = baseContract();
        contractPayload.put("period_start", "2026-08-01");
        contractPayload.put("period_end", "2026-07-01");
        assertThat(validate(contract, contractPayload, ValidationMode.DRAFT).messagesOf("period_end"))
                .containsExactly("履约结束日期不能早于履约开始日期");
    }

    // ================================================================ 附件

    @Test
    @DisplayName("附件：禁止格式（exe）被拒、未知格式被拒、超数量被拒、必填时 ≥1")
    void filePolicyRules() {
        Map<String, Object> payload = baseContract();
        payload.put("attachments", List.of(Map.of("fileName", "virus.exe", "fileSize", 1024)));
        FormValidationReport report = validate(contract, payload, ValidationMode.DRAFT);
        assertThat(report.messagesOf("attachments")).anyMatch(message -> message.contains("不允许上传 exe"));

        payload.put("attachments", List.of(Map.of("fileName", "a.xyz", "fileSize", 1024)));
        assertThat(validate(contract, payload, ValidationMode.DRAFT).messagesOf("attachments"))
                .anyMatch(message -> message.contains("仅支持"));

        payload.put("attachments", List.of(Map.of("fileName", "big.pdf", "fileSize", 51L * 1024 * 1024)));
        assertThat(validate(contract, payload, ValidationMode.DRAFT).messagesOf("attachments"))
                .anyMatch(message -> message.contains("单个文件不超过 50MB"));

        payload.put("attachments", List.of(Map.of("fileName", "ok.pdf", "fileSize", 1024)));
        assertThat(validate(contract, payload, ValidationMode.DRAFT).hasIssueOn("attachments")).isFalse();

        payload.remove("attachments");
        assertThat(validate(contract, payload, ValidationMode.SUBMIT).messagesOf("attachments"))
                .anyMatch(message -> message.contains("合同文本附件"));
    }

    // ================================================================ 图案 / 类型 / 锁定字段

    @Test
    @DisplayName("正则：统一社会信用代码必须 18 位数字与大写字母")
    void patternRule() {
        Map<String, Object> payload = baseContract();
        payload.put("counterparty_credit", "91310000MA1K35XXX");
        assertThat(validate(contract, payload, ValidationMode.DRAFT).messagesOf("counterparty_credit"))
                .containsExactly("统一社会信用代码须为 18 位数字或大写字母");
        payload.put("counterparty_credit", "91310000MA1K35XXXX");
        assertThat(validate(contract, payload, ValidationMode.DRAFT).hasIssueOn("counterparty_credit")).isFalse();
    }

    @Test
    @DisplayName("类型不匹配：布尔字段收到非布尔（非法枚举的另一种形态）被拒")
    void typeMismatch() {
        Map<String, Object> payload = fundPayload("100.00");
        payload.put("urgent", "也许");
        assertThat(validate(fund, payload, ValidationMode.DRAFT).issues())
                .extracting(FieldIssue::rule).contains("typeMismatch");
    }

    @Test
    @DisplayName("locked 字段：合同单 category 固定「经营」，填别的值即被拒")
    void lockedField() {
        Map<String, Object> payload = baseContract();
        payload.put("category", "hr");
        assertThat(validate(contract, payload, ValidationMode.DRAFT).messagesOf("category"))
                .anyMatch(message -> message.contains("模板固定值"));
    }

    @Test
    @DisplayName("一次给全：多个字段同时不合格时，报告里**每一项都在**（不是只报第一个）")
    void reportsAllIssues() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "标".repeat(61));          // 超长
        payload.put("category", "operate");             // 非法枚举
        payload.put("description", "短");                // 太短
        payload.put("involve_cost", true);              // 触发条件必填
        FormValidationReport report = validate(matter, payload, ValidationMode.SUBMIT);
        assertThat(report.issues()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(report.issues()).extracting(FieldIssue::fieldCode)
                .contains("title", "category", "description", "amount", "cost_bearer");

        assertThatThrownBy(report::fail)
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORM_VALIDATION_FAILED)
                .hasMessageContaining("表单字段校验未通过")
                .hasMessageContaining("事项标题");
    }

    @Test
    @DisplayName("去重：纵深防御下同一字段同一规则只出现一条，且保留更完整的文案")
    void collectorDeduplicatesIdenticalIssues() {
        FormValidationReport.Collector collector = FormValidationReport.collector();
        collector.add("amount", "申请金额", "amountRange", "金额必须大于 0 且最多两位小数");
        collector.add("amount", "申请金额", "amountRange", "金额必须大于 0 且最多两位小数");
        collector.add("amount", "申请金额", "amountRange",
                "金额必须大于 0 且最多两位小数（金额禁止使用浮点数提交）");
        collector.add("amount", "申请金额", "amountRange", "金额不能超过 99999999999.99");
        collector.add("other_review_depts", "其他会审部门", "inDict", "其他会审部门取值非法");
        assertThat(collector.build().issues()).hasSize(3);
        assertThat(collector.build().messagesOf("amount")).containsExactly(
                "金额必须大于 0 且最多两位小数（金额禁止使用浮点数提交）",
                "金额不能超过 99999999999.99");
    }

    @Test
    @DisplayName("金额唯一性（unique）：contract_ref 必须指向已通过的单据号")
    void uniqueRuleUsesPort() {
        FormPayloadValidator withChecker = new FormPayloadValidator(
                new FormDictService(new InMemoryDictMapper()), (scope, value) -> false);
        Map<String, Object> payload = fundPayload("100.00");
        payload.put("contract_ref", "OA-2026-999999");
        FormValidationReport report = withChecker.validate(fund, payload, ValidationMode.DRAFT, TODAY, null);
        assertThat(report.messagesOf("contract_ref")).isNotEmpty();
        assertThat(report.issues()).extracting(FieldIssue::rule).contains("unique");
    }

    // ================================================================ 夹具

    private Map<String, Object> fundPayload(Object amount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "8 月供热管网维护款支付");
        payload.put("category", "economy");
        payload.put("plan_category", true);
        payload.put("payment_belong", true);
        if (amount != null) {
            payload.put("amount", amount);
        }
        payload.put("payee", "某某市政工程有限公司");
        payload.put("payee_account", "6222021234567890");
        payload.put("pay_method", "transfer");
        payload.put("pay_date", "2026-08-01");
        payload.put("urgent", false);
        payload.put("attachments", List.of(Map.of("fileName", "invoice.pdf", "fileSize", 2048)));
        return payload;
    }

    private Map<String, Object> baseContract() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "供热管网维护合同");
        payload.put("category", "business");
        payload.put("contract_type", "service");
        payload.put("counterparty", "某某市政工程有限公司");
        payload.put("counterparty_credit", "91310000MA1K35XXXX");
        payload.put("amount", "1250000.00");
        payload.put("period_start", "2026-08-01");
        payload.put("period_end", "2027-07-31");
        payload.put("is_framework", false);
        payload.put("seal_type", "contract_seal");
        payload.put("attachments", List.of(Map.of("fileName", "contract.pdf", "fileSize", 4096)));
        return payload;
    }

    @SuppressWarnings("unused")
    private FormSchema sealSchema() {
        return seal;
    }
}
