package com.oa.admin.bulk;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 导入文件解析与文件级校验单测（import-spec §3.1 / §4.1）。
 *
 * <p>覆盖：UTF-8 BOM（{@code E-ENC-001}）、严格 UTF-8 解码（{@code E-ENC-002}）、
 * 表头逐字一致（{@code E-HDR-001}）、列数（{@code E-ROW-001}）、空行（{@code E-ROW-003}）、
 * 数据区重复表头（{@code E-ROW-004}）、首尾空格告警（{@code W-FMT-001}）、
 * RFC4180 引号/逗号/换行，以及**刻意不校验**「示例数据 3–5 行」（正式导入行数不限）。
 */
class CsvTableTest {

    private static final List<String> ORG_HEADER = ImportKind.ORG.columns();

    private static byte[] csv(String body, boolean bom) {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        if (!bom) {
            return raw;
        }
        byte[] result = new byte[raw.length + 3];
        result[0] = (byte) 0xEF;
        result[1] = (byte) 0xBB;
        result[2] = (byte) 0xBF;
        System.arraycopy(raw, 0, result, 3, raw.length);
        return result;
    }

    private static String header() {
        return String.join(",", ORG_HEADER);
    }

    private static List<ImportFinding> parseOk(byte[] bytes) {
        List<ImportFinding> findings = new ArrayList<>();
        CsvTable table = CsvTable.parse(bytes, ImportKind.ORG, findings);
        assertThat(table).isNotNull();
        assertThat(findings).isEmpty();
        return findings;
    }

    @Test
    @DisplayName("正常解析：BOM + 表头 + 数据行，行号从 2 开始（表头为第 1 行）")
    void parsesValidFile() {
        String body = header() + "\r\n"
                + "集团,集团,集团,,启用,根节点\r\n"
                + "集团/公司A,公司A,公司,集团,启用,\r\n";
        List<ImportFinding> findings = new ArrayList<>();
        CsvTable table = CsvTable.parse(csv(body, true), ImportKind.ORG, findings);

        assertThat(findings).isEmpty();
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(0).line()).isEqualTo(2);
        assertThat(table.rows().get(0).get("org_path")).isEqualTo("集团");
        assertThat(table.rows().get(1).get("parent_path")).isEqualTo("集团");
        assertThat(table.rows().get(1).get("remark")).isEmpty();
    }

    @Test
    @DisplayName("缺少 UTF-8 BOM → E-ENC-001（Excel 双击中文列名会乱码）")
    void missingBomIsRejected() {
        List<ImportFinding> findings = new ArrayList<>();
        CsvTable.parse(csv(header() + "\r\n集团,集团,集团,,启用,\r\n", false), ImportKind.ORG, findings);

        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("E-ENC-001");
            assertThat(finding.isError()).isTrue();
        });
    }

    @Test
    @DisplayName("非 UTF-8（GBK 中文）→ E-ENC-002 且解析终止")
    void nonUtf8IsRejected() {
        byte[] gbk = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, (byte) 0xD6, (byte) 0xD0, (byte) 0xCE,
                (byte) 0xC4};
        List<ImportFinding> findings = new ArrayList<>();
        CsvTable table = CsvTable.parse(gbk, ImportKind.ORG, findings);

        assertThat(table).isNull();
        assertThat(findings).anySatisfy(finding -> assertThat(finding.code()).isEqualTo("E-ENC-002"));
    }

    @Test
    @DisplayName("表头不一致（改名/换序/增减列）→ E-HDR-001 且解析终止")
    void wrongHeaderIsRejected() {
        List<ImportFinding> findings = new ArrayList<>();
        CsvTable table = CsvTable.parse(csv("org_name,org_path,org_type,parent_path,status,remark\r\n"
                + "集团,集团,集团,,启用,\r\n", true), ImportKind.ORG, findings);

        assertThat(table).isNull();
        assertThat(findings).anySatisfy(finding -> assertThat(finding.code()).isEqualTo("E-HDR-001"));
    }

    @Test
    @DisplayName("行结构：列数不符 E-ROW-001、空行 E-ROW-003、重复表头 E-ROW-004，且逐行报告不中断")
    void rowStructureFindings() {
        String body = header() + "\r\n"
                + "集团,集团,集团,启用\r\n"
                + "\r\n"
                + header() + "\r\n"
                + "集团/公司A,公司A,公司,集团,启用,\r\n";
        List<ImportFinding> findings = new ArrayList<>();
        CsvTable table = CsvTable.parse(csv(body, true), ImportKind.ORG, findings);

        assertThat(table.rows()).hasSize(1);
        assertThat(findings).extracting(ImportFinding::code)
                .contains("E-ROW-001", "E-ROW-003", "E-ROW-004");
        assertThat(findings).allSatisfy(finding -> assertThat(finding.isError()).isTrue());
    }

    @Test
    @DisplayName("RFC4180：引号包裹的逗号/换行/双写引号正确解析；首尾空格仅告警 W-FMT-001")
    void rfc4180QuotingAndTrim() {
        String body = header() + "\r\n"
                + "\"集团,总部\",集团,\"集\"\"团\",,启用,\"备注,\r\n换行\"\r\n"
                + " 集团/公司A ,公司A,公司,集团,启用,\r\n";
        List<ImportFinding> findings = new ArrayList<>();
        CsvTable table = CsvTable.parse(csv(body, true), ImportKind.ORG, findings);

        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(0).get("org_path")).isEqualTo("集团,总部");
        assertThat(table.rows().get(0).get("org_type")).isEqualTo("集\"团");
        assertThat(table.rows().get(0).get("remark")).isEqualTo("备注,\r\n换行");
        assertThat(table.rows().get(1).get("org_path")).isEqualTo("集团/公司A");
        assertThat(findings).extracting(ImportFinding::code).containsExactly("W-FMT-001");
        assertThat(findings.get(0).isError()).isFalse();
        assertThat(findings.get(0).column()).isEqualTo("org_path");
        assertThat(findings.get(0).value()).isEqualTo(" 集团/公司A ");
    }

    @Test
    @DisplayName("正式导入不限行数（E-ROW-002 的 3–5 行只约束交付模板，不约束服务端导入）")
    void rowCountIsNotRestricted() {
        StringBuilder body = new StringBuilder(header()).append("\r\n");
        for (int i = 0; i < 30; i++) {
            body.append("集团/公司").append(i).append(",公司").append(i).append(",公司,集团,启用,\r\n");
        }
        List<ImportFinding> findings = new ArrayList<>();
        CsvTable table = CsvTable.parse(csv(body.toString(), true), ImportKind.ORG, findings);

        assertThat(table.rows()).hasSize(30);
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("五类模板的表头与 import-spec 逐字一致（防模板被误改）")
    void kindHeadersMatchSpec() {
        assertThat(ImportKind.ORG.columns()).containsExactly("org_path", "org_name", "org_type", "parent_path",
                "status", "remark");
        assertThat(ImportKind.USER.columns()).containsExactly("account", "employee_no", "name", "phone", "email",
                "company_path", "dept_path", "status", "remark");
        assertThat(ImportKind.ORG_LEADER.columns()).containsExactly("org_path", "user_account", "leader_type",
                "sort", "business_line", "remark");
        assertThat(ImportKind.USER_POSITION.columns()).containsExactly("user_account", "org_path", "post_name",
                "is_primary", "remark");
        assertThat(ImportKind.USER_ROLE.columns()).containsExactly("user_account", "role_code", "scope_org_path",
                "remark");
        assertThat(ImportKind.of("user_role")).isEqualTo(ImportKind.USER_ROLE);
        assertThat(ImportKind.of("unknown")).isNull();
    }
}
