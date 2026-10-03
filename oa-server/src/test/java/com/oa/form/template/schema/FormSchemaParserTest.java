package com.oa.form.template.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.json.JsonText;
import com.oa.form.FormSchemaFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>2b.1 表单模板引擎：{@code form_schema_json} 的解析与结构自检</b>
 * （doc/templates.md §2.1 / §2.2 / §2.3；doc/forms.md §11）。
 *
 * <p>解析对象是 {@code oa-deploy/sql/03-templates.sql} 里的**真实四条 schema**
 * （matter 9 / fund 12 / contract 14 / seal 12 个字段，见 doc/templates.md §1.5 与 forms.md §2–§5），
 * 因此这些断言同时也是「种子脚本与契约一致」的回归网。
 */
class FormSchemaParserTest {

    @Test
    @DisplayName("真源四条 schema 均可解析，字段数与 templates.md §1.5 逐类一致")
    void parsesAllSeededSchemas() {
        Map<String, FormSchema> schemas = FormSchemaFixtures.schemas();
        assertThat(schemas).containsOnlyKeys("matter", "fund", "contract", "seal");
        assertThat(schemas.get("matter").fields()).hasSize(9);
        assertThat(schemas.get("fund").fields()).hasSize(12);
        assertThat(schemas.get("contract").fields()).hasSize(14);
        assertThat(schemas.get("seal").fields()).hasSize(12);
        for (Map.Entry<String, FormSchema> entry : schemas.entrySet()) {
            FormSchema schema = entry.getValue();
            assertThat(schema.formType()).isEqualTo(entry.getKey());
            assertThat(schema.templateCode()).isEqualTo(entry.getKey());
            assertThat(schema.schemaVersion()).isEqualTo(1);
            assertThat(schema.sections()).as("%s 有字段分组", entry.getKey()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("字典绑定落在白名单内，且只在 select/multiselect 上出现")
    void dictBindingsAreWhitelisted() {
        Map<String, String> matter = FormSchemaFixtures.schema("matter").dictBindings();
        assertThat(matter).containsEntry("category", "matter_category");
        assertThat(FormSchemaFixtures.schema("fund").dictBindings())
                .containsEntry("pay_method", "payment_method")
                .containsEntry("category", "matter_category");
        assertThat(FormSchemaFixtures.schema("contract").dictBindings())
                .containsEntry("contract_type", "contract_type")
                .containsEntry("seal_type", "seal_type")
                .containsEntry("other_review_depts", "review_dept_other");
        assertThat(FormSchemaFixtures.schema("seal").dictBindings())
                .containsEntry("cert_name", "cert_type")
                .containsEntry("return_status", "return_status")
                .containsEntry("seal_type", "seal_type");
    }

    @Test
    @DisplayName("locked / readonlyAfterSubmit 逐字段口径：合同与印鉴的 category 锁定，附件可写")
    void readonlyFlags() {
        FormSchema contract = FormSchemaFixtures.schema("contract");
        assertThat(contract.field("category").orElseThrow().locked()).isTrue();
        assertThat(FormSchemaFixtures.schema("seal").field("category").orElseThrow().locked()).isTrue();
        assertThat(contract.field("attachments").orElseThrow().readonlyAfterSubmit())
                .as("附件是补件可写字段（doc/templates.md §2.2：readonlyAfterSubmit=false 仅用于白名单内字段）")
                .isFalse();
        assertThat(FormSchemaFixtures.schema("matter").field("title").orElseThrow().readonlyAfterSubmit()).isTrue();
    }

    @Test
    @DisplayName("非法 schema 一律 40008（不降级）：缺字段 / 未知类型 / 非白名单字典 / 重名 / 悬空分组")
    void rejectsInvalidSchemas() {
        assertThatThrownBy(() -> FormSchemaParser.parse(null, "matter", 1))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FLOW_DEFINITION_INVALID);

        assertThatThrownBy(() -> FormSchemaParser.parse("{\"form_type\":\"matter\"}", "matter", 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("template_code");

        assertThatThrownBy(() -> FormSchemaParser.parse(
                "{\"form_type\":\"matter\",\"template_code\":\"matter\",\"schema_version\":1,"
                        + "\"fields\":[{\"code\":\"a\",\"label\":\"A\",\"type\":\"rich_text\",\"rules\":[]}]}",
                "matter", 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("14 种字段类型");

        assertThatThrownBy(() -> FormSchemaParser.parse(
                "{\"form_type\":\"matter\",\"template_code\":\"matter\",\"schema_version\":1,"
                        + "\"fields\":[{\"code\":\"category\",\"label\":\"类别\",\"type\":\"select\","
                        + "\"rules\":[],\"optionsSource\":{\"kind\":\"dict\",\"dictType\":\"category\"}}]}",
                "matter", 1))
                .as("旧 dict_type 名 category 已作废（doc/forms.md §6 对照表 / doc/enums.md §14）")
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不在字典类型白名单内");

        assertThatThrownBy(() -> FormSchemaParser.parse(
                "{\"form_type\":\"matter\",\"template_code\":\"matter\",\"schema_version\":1,"
                        + "\"fields\":[{\"code\":\"a\",\"label\":\"A\",\"type\":\"text\",\"rules\":[]},"
                        + "{\"code\":\"a\",\"label\":\"A2\",\"type\":\"text\",\"rules\":[]}]}",
                "matter", 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("重复");

        assertThatThrownBy(() -> FormSchemaParser.parse(
                "{\"form_type\":\"matter\",\"template_code\":\"matter\",\"schema_version\":1,"
                        + "\"sections\":[{\"id\":\"s\",\"title\":\"S\",\"fields\":[\"ghost\"]}],"
                        + "\"fields\":[{\"code\":\"a\",\"label\":\"A\",\"type\":\"text\",\"rules\":[]}]}",
                "matter", 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登记的字段码");

        assertThatThrownBy(() -> FormSchemaParser.parse(
                "{\"form_type\":\"matter\",\"template_code\":\"matter\",\"schema_version\":2,"
                        + "\"fields\":[{\"code\":\"a\",\"label\":\"A\",\"type\":\"text\",\"rules\":[]}]}",
                "matter", 1))
                .as("V-08：schema_version 必须等于 flow_template.version")
                .isInstanceOf(BizException.class)
                .hasMessageContaining("schema_version");
    }

    @Test
    @DisplayName("单据类型码白名单：matter/fund/contract/seal，其余 400")
    void formTypeWhitelist() {
        assertThat(FormSchemaParser.requireKnownFormType(" FUND ")).isEqualTo("fund");
        assertThatThrownBy(() -> FormSchemaParser.requireKnownFormType("invoice"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("doc/enums.md §10.2");
    }

    @Test
    @DisplayName("字段摘要出参含 printLabel / printVisible（打印渲染驱动，doc/templates.md §5.1）")
    void fieldViewCarriesPrintKeys() {
        Map<String, Object> view = FormSchemaFixtures.schema("matter").field("category").orElseThrow().view();
        assertThat(view).containsEntry("printLabel", "事项分类").containsEntry("printVisible", true);
        Map<String, Object> involveCost = FormSchemaFixtures.schema("matter").field("involve_cost")
                .orElseThrow().view();
        assertThat(involveCost)
                .as("doc/forms.md §10：involve_cost 不占独立字段行（printVisible=false）")
                .containsEntry("printVisible", false);
    }

    // ================================================================ 规则参数出参（rules / ruleDetails）

    @Test
    @DisplayName("字段摘要出参的规则：既有 rules（类型名数组）不变，新增 ruleDetails 如实给出模板声明的参数")
    void fieldViewCarriesRuleParameters() {
        Map<String, Object> view = FormSchemaFixtures.schema("matter").field("cc_users").orElseThrow().view();

        // ① 既有形状一字未改（不得删改既有字段）
        assertThat(view.get("rules")).as("既有形状：规则类型名数组").isEqualTo(java.util.List.of("pickerLimit"));

        // ② 新增：规则参数（模板里 cc_users 声明 {"type":"pickerLimit","max":20,"message":"抄送人最多选择 20 人"}）
        List<Map<String, Object>> details = ruleDetails(view);
        assertThat(details).hasSize(1);
        assertThat(details.get(0))
                .containsEntry("type", "pickerLimit")
                .containsEntry("max", 20)
                .containsEntry("message", "抄送人最多选择 20 人");
        assertThat(details.get(0).get("type"))
                .as("ruleDetails 与 rules **下标一一对应**").isEqualTo(((List<?>) view.get("rules")).get(0));
    }

    @Test
    @DisplayName("规则参数：模板声明 5 则出参 5；**未声明就不出该键**（不把服务端默认值伪装成模板事实）")
    void ruleDetailsMirrorOnlyWhatTheTemplateDeclares() {
        FormFieldDef declared = FormFieldDef.from(JsonText.read("{\"code\":\"cc_users\",\"label\":\"抄送人\","
                + "\"type\":\"user\",\"rules\":[{\"type\":\"pickerLimit\",\"max\":5,\"message\":\"最多 5 人\"}]}"));
        assertThat(ruleDetails(declared.view()).get(0))
                .containsEntry("max", 5).containsEntry("message", "最多 5 人");

        FormFieldDef undeclared = FormFieldDef.from(JsonText.read("{\"code\":\"cc_users\",\"label\":\"抄送人\","
                + "\"type\":\"user\",\"rules\":[{\"type\":\"pickerLimit\",\"message\":\"最多 20 人\"}]}"));
        assertThat(ruleDetails(undeclared.view()).get(0))
                .containsEntry("type", "pickerLimit")
                .containsEntry("message", "最多 20 人")
                .as("未声明的 max 不出现（此时生效的是服务端默认 20，属另一层口径）")
                .doesNotContainKey("max");
    }

    @Test
    @DisplayName("规则参数：嵌套条件对象 / 数组 / 定点数如实转写（when、allowExt、scale）")
    void ruleDetailsCarryNestedParameters() {
        FormSchema matter = FormSchemaFixtures.schema("matter");
        // cost_bearer 的 conditionalRequired 带 when 条件对象
        List<Map<String, Object>> costBearer = ruleDetails(matter.field("cost_bearer").orElseThrow().view());
        Map<String, Object> conditional = costBearer.stream()
                .filter(item -> "conditionalRequired".equals(item.get("type"))).findFirst().orElseThrow();
        assertThat(conditional.get("when")).as("嵌套条件对象递归转成普通 Map").isInstanceOf(Map.class);
        assertThat(objectMap(conditional.get("when")))
                .containsEntry("field", "involve_cost").containsEntry("op", "eq").containsEntry("value", true);
        assertThat(conditional).containsEntry("message", "涉及费用时，费用承担主体为必填");

        // 附件字段的 filePolicy 带数组（allowExt / denyExt）
        FormSchema fund = FormSchemaFixtures.schema("fund");
        Map<String, Object> filePolicy = ruleDetails(fund.field("attachments").orElseThrow().view()).stream()
                .filter(item -> "filePolicy".equals(item.get("type"))).findFirst().orElseThrow();
        assertThat(filePolicy.get("allowExt")).as("数组如实转成 List").isInstanceOf(List.class);
        assertThat(stringList(filePolicy.get("allowExt"))).contains("pdf", "xlsx").doesNotContain("exe");
        assertThat(filePolicy).containsEntry("maxSizeMb", 50).containsEntry("maxCount", 20);

        // amountRange 的 scale / max
        Map<String, Object> amountRange = ruleDetails(fund.field("amount").orElseThrow().view()).stream()
                .filter(item -> "amountRange".equals(item.get("type"))).findFirst().orElseThrow();
        assertThat(amountRange).containsEntry("scale", 2).containsEntry("min", "0.01");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> ruleDetails(Map<String, Object> fieldView) {
        return (List<Map<String, Object>>) fieldView.get("ruleDetails");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        return (List<String>) value;
    }

    // ================================================================ 版本同步

    @Test
    @DisplayName("模板开新版本：schema_version 必须同步到新版本号（否则该单据类型建草稿全部 40008）")
    void syncSchemaVersionOnNewVersion() {
        String base = FormSchemaFixtures.rawSchemas().get("fund");
        assertThat(FormSchemaParser.declaredSchemaVersion(base)).isEqualTo(1);

        String upgraded = FormSchemaParser.syncSchemaVersion(base, 5);
        assertThat(FormSchemaParser.declaredSchemaVersion(upgraded)).isEqualTo(5);
        assertThat(upgraded)
                .as("只改这一个键，其余字段定义逐字保留")
                .doesNotContain("\"schema_version\":1")
                .contains("\"form_type\":\"fund\"");
        // 改写后必须能被解析器按新版本接受（这正是「建草稿不再 40008」的机检点）
        assertThat(FormSchemaParser.parse(upgraded, "fund", 5).schemaVersion()).isEqualTo(5);

        // 边界：空/非对象/非法版本号一律原样返回（不阻断「只配流程不配表单」的模板）
        assertThat(FormSchemaParser.syncSchemaVersion(null, 5)).isNull();
        assertThat(FormSchemaParser.syncSchemaVersion("   ", 5)).isEqualTo("   ");
        assertThat(FormSchemaParser.syncSchemaVersion("[1,2]", 5)).isEqualTo("[1,2]");
        assertThat(FormSchemaParser.syncSchemaVersion("not-json", 5)).isEqualTo("not-json");
        assertThat(FormSchemaParser.syncSchemaVersion(base, null)).isEqualTo(base);
        assertThat(FormSchemaParser.syncSchemaVersion(base, 0)).isEqualTo(base);
    }
}
