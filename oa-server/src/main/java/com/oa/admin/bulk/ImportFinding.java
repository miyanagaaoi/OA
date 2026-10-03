package com.oa.admin.bulk;

import java.util.List;

/**
 * 校验发现（import-spec §5.1 的错误报告行）。
 *
 * @param severity {@code error} / {@code warning}（§4.1 的级别列）
 * @param code     错误码，如 {@code E-USER-003}（§5.2 码段）
 * @param file     文件名（{@code user.csv}）
 * @param line     数据行号（**文件行号**，表头为第 1 行，便于填报人定位）
 * @param column   列名
 * @param value    触发值（原文，便于复制搜索）
 * @param message  中文说明（直白句式，import-spec §5.1）
 */
public record ImportFinding(
        String severity,
        String code,
        String file,
        int line,
        String column,
        String value,
        String message) {

    public static final String ERROR = "error";

    public static final String WARNING = "warning";

    public static ImportFinding error(String code, String file, int line, String column, String value, String message) {
        return new ImportFinding(ERROR, code, file, line, column, value, message);
    }

    public static ImportFinding warning(String code, String file, int line, String column, String value, String message) {
        return new ImportFinding(WARNING, code, file, line, column, value, message);
    }

    public static ImportFinding error(String code, String file, int line, String message) {
        return error(code, file, line, null, null, message);
    }

    public boolean isError() {
        return ERROR.equals(severity);
    }

    /** 报告内展示用的「行号 + 列 + 值 + 错误码 + 说明」五元组。 */
    public List<String> cells() {
        return List.of(String.valueOf(line), column == null ? "" : column, value == null ? "" : value, code, message);
    }
}
