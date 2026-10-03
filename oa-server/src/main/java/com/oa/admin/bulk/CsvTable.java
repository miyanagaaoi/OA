package com.oa.admin.bulk;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入 CSV 解析（{@code import-spec.md §3.1 / §4.1} 的文件级校验 + RFC4180 解析）。
 *
 * <p>校验点与错误码一一对应：
 * <ul>
 *   <li>{@code E-ENC-001}：必须以 UTF-8 BOM（{@code EF BB BF}）开头；</li>
 *   <li>{@code E-ENC-002}：必须能被 UTF-8 **严格**解码（不得含替换字符、不得为 UTF-16）；</li>
 *   <li>{@code E-HDR-002} / {@code E-HDR-001}：必须有表头行，且列名与顺序**逐字**一致；</li>
 *   <li>{@code E-ROW-001}：每行列数等于表头列数；</li>
 *   <li>{@code E-ROW-003} / {@code E-ROW-004}：不得有空行 / 数据区重复表头；</li>
 *   <li>{@code W-FMT-001}：单元格首尾空格（按 trim 处理，仅告警）。</li>
 * </ul>
 *
 * <p><b>刻意不实现</b> {@code E-ROW-002}（「3–5 行示例数据」）：该规则约束的是**交付模板**的
 * 示例行数（供 CI 校验），正式数据行数不限（§3.1「正式数据行数不限，300+ 人应拆分单文件一次导入」），
 * 服务端若照搬会把真实导入全部拒绝。
 */
public final class CsvTable {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final List<String> header;

    private final List<Row> rows;

    private CsvTable(List<String> header, List<Row> rows) {
        this.header = List.copyOf(header);
        this.rows = List.copyOf(rows);
    }

    public List<String> header() {
        return header;
    }

    public List<Row> rows() {
        return rows;
    }

    /** 数据行（行号 = 文件内 1-based 行号，表头为 1）。 */
    public record Row(int line, List<String> values, Map<String, String> byColumn) {

        /** 按列名取值（不存在返回 {@code null}）。 */
        public String get(String column) {
            return byColumn.get(column);
        }
    }

    /**
     * 解析导入文件。
     *
     * @param bytes     文件字节
     * @param kind      模板类型（决定期望表头）
     * @param findings  输出：文件级与行级发现（追加，不抛异常 —— 报告必须一次列全）
     * @return 解析结果；文件级 error（编码/表头）时返回 {@code null}
     */
    public static CsvTable parse(byte[] bytes, ImportKind kind, List<ImportFinding> findings) {
        String file = kind.fileName();
        String text = decode(bytes, file, findings);
        if (text == null) {
            return null;
        }
        boolean hasBom = hasBom(bytes);
        if (!hasBom) {
            findings.add(ImportFinding.error("E-ENC-001", file, 0, "（文件）", null,
                    "文件缺少 UTF-8 BOM（EF BB BF）：Excel 双击打开中文列名会乱码（import-spec §4.1）"));
        }
        List<List<String>> raw = splitRows(text);
        if (raw.isEmpty()) {
            findings.add(ImportFinding.error("E-HDR-002", file, 0, "（表头）", null, "文件缺少表头行"));
            return null;
        }
        List<String> header = trimAll(raw.get(0));
        List<String> expected = kind.columns();
        if (!header.equals(expected)) {
            findings.add(ImportFinding.error("E-HDR-001", file, 1, "（表头）", String.join(",", header),
                    "表头与模板不一致：期望 " + String.join(",", expected) + "，实际 " + String.join(",", header)));
            return null;
        }
        // 逐行结构校验
        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < raw.size(); index++) {
            List<String> cells = raw.get(index);
            int line = index + 1;
            if (isBlankRow(cells)) {
                findings.add(ImportFinding.error("E-ROW-003", file, line, "（行）", null, "存在空行，请删除"));
                continue;
            }
            if (trimAll(cells).equals(expected)) {
                findings.add(ImportFinding.error("E-ROW-004", file, line, "（行）", null,
                        "数据区重复出现表头行，请删除"));
                continue;
            }
            if (cells.size() != header.size()) {
                findings.add(ImportFinding.error("E-ROW-001", file, line, "（行）", String.valueOf(cells.size()),
                        "列数为 " + cells.size() + "，与表头列数 " + header.size() + " 不一致"
                                + "（值内含英文逗号时必须用双引号包裹）"));
                continue;
            }
            Map<String, String> byColumn = new LinkedHashMap<>();
            for (int column = 0; column < header.size(); column++) {
                String rawValue = cells.get(column) == null ? "" : cells.get(column);
                String value = rawValue.trim();
                if (!rawValue.equals(value)) {
                    findings.add(ImportFinding.warning("W-FMT-001", file, line, header.get(column), rawValue,
                            "单元格首尾有空格（系统按 trim 处理），建议清理"));
                }
                byColumn.put(header.get(column), value);
            }
            rows.add(new Row(line, trimAll(cells), byColumn));
        }
        return new CsvTable(header, rows);
    }

    private static String decode(byte[] bytes, String file, List<ImportFinding> findings) {
        if (bytes == null || bytes.length == 0) {
            findings.add(ImportFinding.error("E-ENC-002", file, 0, "（文件）", null, "文件为空或无法读取"));
            return null;
        }
        int offset = hasBom(bytes) ? BOM.length : 0;
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            String text = decoder.decode(java.nio.ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
            if (text.indexOf('\uFFFD') >= 0) {
                findings.add(ImportFinding.error("E-ENC-002", file, 0, "（文件）", null,
                        "文件含替换字符（很可能不是 UTF-8，例如 UTF-16 或 GBK）"));
                return null;
            }
            return text;
        } catch (CharacterCodingException ex) {
            findings.add(ImportFinding.error("E-ENC-002", file, 0, "（文件）", null,
                    "文件不是合法的 UTF-8 编码，请另存为「CSV UTF-8（逗号分隔）」"));
            return null;
        }
    }

    /** 是否有 UTF-8 BOM。 */
    public static boolean hasBom(byte[] bytes) {
        return bytes != null && bytes.length >= 3
                && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2];
    }

    /** RFC4180 行拆分：支持 CRLF/LF/CR、双引号包裹、内部 {@code ""} 转义、引号内换行。 */
    static List<List<String>> splitRows(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean cellStarted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
                continue;
            }
            switch (ch) {
                case '"' -> {
                    quoted = true;
                    cellStarted = true;
                }
                case ',' -> {
                    cells.add(cell.toString());
                    cell.setLength(0);
                    cellStarted = false;
                }
                case '\r' -> {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    cells.add(cell.toString());
                    cell.setLength(0);
                    rows.add(cells);
                    cells = new ArrayList<>();
                    cellStarted = false;
                }
                case '\n' -> {
                    cells.add(cell.toString());
                    cell.setLength(0);
                    rows.add(cells);
                    cells = new ArrayList<>();
                    cellStarted = false;
                }
                default -> {
                    cell.append(ch);
                    cellStarted = true;
                }
            }
        }
        if (cellStarted || cell.length() > 0 || !cells.isEmpty()) {
            cells.add(cell.toString());
            rows.add(cells);
        }
        return rows;
    }

    private static boolean isBlankRow(List<String> cells) {
        for (String cell : cells) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static List<String> trimAll(List<String> cells) {
        List<String> result = new ArrayList<>(cells.size());
        for (String cell : cells) {
            result.add(cell == null ? "" : cell.trim());
        }
        return result;
    }
}
