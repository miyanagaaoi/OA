package com.oa.identity.app;

/**
 * CSV 生成工具 —— <b>纯函数</b>（导出主数据用）。
 *
 * <p>口径依据 doc/import-spec.md：
 * <ul>
 *   <li>§3.1 / §4.1：文件为 **UTF-8 带 BOM**（{@code EF BB BF}）；无 BOM 时校验器报
 *       {@code E-ENC-001}（Excel 双击中文列名乱码）→ 导出必须带 BOM，否则「导出 → 再导入」闭环断裂；</li>
 *   <li>§3.1：分隔符英文逗号；值中含逗号/双引号/换行时必须用英文双引号包裹，内部双引号写 {@code ""}（RFC4180）；</li>
 *   <li>§9.1「往返约束」：导出列名、顺序、取值口径必须与导入模板一致，
 *       且导出物必须能被 {@code tools/check-import-csv.js} 直接通过。</li>
 * </ul>
 */
public final class CsvSupport {

    /** UTF-8 BOM（import-spec §4.1 硬要求）。 */
    public static final String UTF8_BOM = "\uFEFF";

    /** 行分隔符：CRLF（Excel 友好，且 RFC4180 允许）。 */
    public static final String CRLF = "\r\n";

    private CsvSupport() {
    }

    /**
     * RFC4180 转义：空值 → 空串；含 {@code , " \r \n} → 双引号包裹并转义内部双引号。
     *
     * <p>注意：{@code null} 与空串都输出空字段（import-spec §3.1「允许留空表示 NULL」），
     * **不得**用 {@code -} 或空格代替空。
     */
    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == ',' || ch == '"' || ch == '\r' || ch == '\n') {
                needsQuote = true;
                break;
            }
        }
        if (!needsQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** 用逗号拼接一行（自动转义，不追加换行）。 */
    public static String line(String... values) {
        StringBuilder builder = new StringBuilder();
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(escape(values[i]));
            }
        }
        return builder.toString();
    }

    /** 追加一行（含 CRLF）。 */
    public static void appendLine(StringBuilder builder, String... values) {
        builder.append(line(values)).append(CRLF);
    }
}
