package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 导出用 CSV 生成（**纯逻辑单测**）与「导出 → 再导入」往返约束
 * （import-spec §3.1 RFC4180 转义、§4.1 UTF-8 带 BOM、§9.1 导出列与模板一致）。
 */
class CsvSupportTest {

    @Test
    @DisplayName("UTF-8 BOM 常量必须是 EF BB BF（否则校验器报 E-ENC-001）")
    void bomIsMandatory() {
        assertThat(CsvSupport.UTF8_BOM).hasSize(1);
        byte[] bytes = CsvSupport.UTF8_BOM.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(bytes).containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    @DisplayName("RFC4180 转义：逗号/双引号/换行才加引号，内部双引号翻倍；空值与 null 都是空字段")
    void escapeRules() {
        assertThat(CsvSupport.escape(null)).isEmpty();
        assertThat(CsvSupport.escape("")).isEmpty();
        assertThat(CsvSupport.escape("集团/公司A/部门1")).isEqualTo("集团/公司A/部门1");
        assertThat(CsvSupport.escape("经营,投资")).isEqualTo("\"经营,投资\"");
        assertThat(CsvSupport.escape("含\"引号\"")).isEqualTo("\"含\"\"引号\"\"\"");
        assertThat(CsvSupport.escape("两\n行")).isEqualTo("\"两\n行\"");
        assertThat(CsvSupport.escape("回车\r")).isEqualTo("\"回车\r\"");
    }

    @Test
    @DisplayName("整行拼接：表头逐字与 import-spec §9.1 的 org.csv 一致，行尾用 CRLF")
    void orgExportHeaderAndLine() {
        StringBuilder builder = new StringBuilder(CsvSupport.UTF8_BOM);
        CsvSupport.appendLine(builder, "org_path", "org_name", "org_type", "parent_path", "status", "remark");
        assertThat(builder.toString())
                .isEqualTo(CsvSupport.UTF8_BOM
                        + "org_path,org_name,org_type,parent_path,status,remark" + CsvSupport.CRLF);

        // 集团行的 parent_path 必须留空（import-spec §3.2「集团行留空」）
        assertThat(CsvSupport.line("集团", "集团", "集团", null, "启用", null))
                .isEqualTo("集团,集团,集团,,启用,");
        // 值中含逗号（组织名可能出现「公司A,分公司」）必须被引号包裹，保证往返可解析
        assertThat(CsvSupport.line("集团,公司A", "公司A,分公司", "公司", "集团", "启用", "备注"))
                .isEqualTo("\"集团,公司A\",\"公司A,分公司\",公司,集团,启用,备注");
    }
}
