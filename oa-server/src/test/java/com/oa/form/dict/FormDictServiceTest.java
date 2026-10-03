package com.oa.form.dict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>2b.4 字典与下拉</b>单测：取值来自 {@code sys_dict_item}、类别是配置项、历史取值是快照。
 */
class FormDictServiceTest {

    private InMemoryDictMapper mapper;
    private FormDictService service;

    @BeforeEach
    void setUp() {
        mapper = new InMemoryDictMapper();
        service = new FormDictService(mapper);
    }

    @Test
    @DisplayName("白名单 8 类，种子 37 行（doc/dict-seed.md §11 预期行数）")
    void whitelistAndRowCount() {
        assertThat(DictType.codes()).containsExactly("matter_category", "contract_type", "seal_type",
                "cert_type", "payment_method", "return_status", "group_dept", "review_dept_other");
        assertThat(mapper.totalRows()).as("5+6+6+5+4+3+4+4 = 37").isEqualTo(37);
        assertThat(service.counts()).containsEntry("matter_category", 5).containsEntry("return_status", 3);
    }

    @Test
    @DisplayName("下拉取值：启用项按 sort_no 排序；未知字典类型一律 400（不返回空列表）")
    void optionsFromDict() {
        List<DictItem> items = service.options("matter_category");
        assertThat(items).extracting(DictItem::itemCode)
                .containsExactly("business", "economy", "admin", "hr", "invest");
        assertThat(items.get(0).itemName()).isEqualTo("经营");

        assertThatThrownBy(() -> service.options("category"))
                .as("旧 dict_type 名 category 已作废（doc/enums.md §14）")
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知的字典类型");
        assertThatThrownBy(() -> service.options("plan_category"))
                .as("plan_category 是布尔 checkbox，不是字典（doc/dict-seed.md §7）")
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("取值校验：停用项与未知 code 判非法；未知字典类型判非法")
    void optionValidation() {
        assertThat(service.isValidOption("return_status", "returned")).isTrue();
        assertThat(service.isValidOption("return_status", "gone")).isFalse();
        assertThat(service.isValidOption("nope", "returned")).isFalse();

        mapper.add("matter_category", "rd", "研发", 60, "inactive");
        service.refresh();
        assertThat(service.isValidOption("matter_category", "rd"))
                .as("停用项不再可选（status 列语义）")
                .isFalse();
        mapper.add("matter_category", "dev", "研发中心", 70, "active");
        service.refresh();
        assertThat(service.isValidOption("matter_category", "dev"))
                .as("后台新增类别即刻可选、无需发版（doc/prd-0.1.md AC-03）")
                .isTrue();
    }

    @Test
    @DisplayName("快照语义：字典删除后，历史单据的旧 code 仍可解析出展示名且不抛异常")
    void snapshotSemantics() {
        assertThat(service.displayName("matter_category", "invest")).isEqualTo("投资");
        mapper.remove("matter_category", "invest");
        service.refresh();
        assertThat(service.displayName("matter_category", "invest"))
                .as("字典改动不回溯历史单据（doc/templates.md §4.2 / doc/enums.md §10.1 约束 4）")
                .isEqualTo("invest");
        assertThat(service.snapshotNames("matter_category", List.of("invest", "economy")))
                .extracting(entry -> entry.get("code") + "=" + entry.get("name"))
                .containsExactly("invest=invest", "economy=经济");
    }

    @Test
    @DisplayName("多选展示名：去重保序（打印稿「逗号分隔」口径）")
    void multiSelectNames() {
        assertThat(service.displayNames("review_dept_other",
                List.of("econ_dev", "finance", "econ_dev", "  ")))
                .containsExactly("经发部", "财务部");
    }

    @Test
    @DisplayName("缓存刷新：refresh() 清空启用项缓存后回源")
    void refreshClearsCache() {
        service.options("seal_type");
        assertThat(service.refresh()).isEqualTo(1);
        assertThat(service.refresh()).isZero();
    }

    @Test
    @DisplayName("计数出参覆盖全部 8 类（披露用）")
    void countsCoverAllTypes() {
        Map<String, Object> counts = service.counts();
        assertThat(counts).hasSize(8);
        assertThat(counts.values().stream().mapToInt(value -> (Integer) value).sum()).isEqualTo(37);
    }
}
