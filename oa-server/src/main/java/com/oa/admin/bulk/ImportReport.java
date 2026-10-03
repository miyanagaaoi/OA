package com.oa.admin.bulk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导入校验报告（import-spec §5.4 统计字段 + §5.5 机器可读结构）。
 *
 * <p>出参结构（JSON）：
 * <pre>
 * {
 *   "kind": "user", "file": "user.csv", "dryRun": true, "ok": false,
 *   "totalRows": 5, "passedRows": 3, "failedRows": 2, "added": 0, "updated": 0, "skipped": 0,
 *   "errors": 2, "warnings": 1,
 *   "byCode": [{ "code": "E-USER-003", "count": 2, "suggestion": "…" }],
 *   "suggestions": [{ "code": "E-USER-003", "count": 2, "suggestion": "…" }],
 *   "findings": [{ "severity": "error", "code": "E-USER-003", "file": "user.csv",
 *                  "line": 5, "column": "phone", "value": "1390000", "message": "…" }],
 *   "notes": ["…"]
 * }
 * </pre>
 *
 * <p>口径：{@code failedRows} 按**行去重**（一行多错只计一行，§5.4）。
 */
public class ImportReport {

    private final String kind;

    private final String file;

    private final String label;

    private final boolean dryRun;

    private final List<ImportFinding> findings = new ArrayList<>();

    private final List<String> notes = new ArrayList<>();

    private int totalRows;

    private int added;

    private int updated;

    private int skipped;

    /**
     * 新增人员的**初始口令清单**（import-spec T-03：随机口令 + 加密清单线下分发）。
     *
     * <p>仅出现在**本次 commit 的响应**里，不落库、不进审计日志（控制器标
     * {@code @Audited(recordAfter=false)}）；preview 阶段不生成口令（§6.2）。
     */
    private final List<Map<String, String>> credentials = new ArrayList<>();

    public ImportReport(ImportKind kind, boolean dryRun) {
        this.kind = kind.code();
        this.file = kind.fileName();
        this.label = kind.label();
        this.dryRun = dryRun;
    }

    public void add(ImportFinding finding) {
        if (finding != null) {
            findings.add(finding);
        }
    }

    public void addAll(List<ImportFinding> items) {
        if (items != null) {
            items.forEach(this::add);
        }
    }

    public void note(String text) {
        if (text != null && !text.isBlank()) {
            notes.add(text);
        }
    }

    public void totalRows(int total) {
        this.totalRows = total;
    }

    public void stats(int addedRows, int updatedRows, int skippedRows) {
        this.added = addedRows;
        this.updated = updatedRows;
        this.skipped = skippedRows;
    }

    /** 追加一条初始口令（仅 commit 阶段）。 */
    public void credential(String account, String name, String initialPassword) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("account", account);
        item.put("name", name);
        item.put("initialPassword", initialPassword);
        item.put("note", "首次登录强制改密；请通过线下加密清单分发（import-spec T-03）");
        credentials.add(item);
    }

    public List<Map<String, String>> getCredentials() {
        return credentials;
    }

    public String getKind() {
        return kind;
    }

    public String getFile() {
        return file;
    }

    public String getLabel() {
        return label;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    /** 是否通过（无 error）；commit 只在为 true 时执行。 */
    public boolean isOk() {
        return errors() == 0;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getPassedRows() {
        return Math.max(0, totalRows - failedRows());
    }

    /** 存在 error 的行数（按行去重）。 */
    public int getFailedRows() {
        return failedRows();
    }

    public int getErrors() {
        return errors();
    }

    public int getWarnings() {
        return warnings();
    }

    public int getAdded() {
        return added;
    }

    public int getUpdated() {
        return updated;
    }

    public int getSkipped() {
        return skipped;
    }

    public List<ImportFinding> getFindings() {
        return findings;
    }

    public List<String> getNotes() {
        return notes;
    }

    /** 错误码分布 + 修正建议（按错误码去重，便于「一次修一类问题」，§5.4）。 */
    public List<Map<String, Object>> getSuggestions() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> warningsSeen = new LinkedHashSet<>();
        for (ImportFinding finding : findings) {
            if (finding.isError()) {
                counts.merge(finding.code(), 1, Integer::sum);
            } else {
                warningsSeen.add(finding.code());
            }
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", entry.getKey());
            item.put("count", entry.getValue());
            item.put("suggestion", ImportCodes.suggestion(entry.getKey()));
            items.add(item);
        }
        return items;
    }

    /** {@code byCode}：错误码分布的别名（报告头部字段，与 {@link #getSuggestions()} 同源）。 */
    public List<Map<String, Object>> getByCode() {
        return getSuggestions();
    }

    public int errors() {
        int count = 0;
        for (ImportFinding finding : findings) {
            if (finding.isError()) {
                count++;
            }
        }
        return count;
    }

    public int warnings() {
        int count = 0;
        for (ImportFinding finding : findings) {
            if (!finding.isError()) {
                count++;
            }
        }
        return count;
    }

    /** 失败行数（按行号去重）。 */
    public int failedRows() {
        Set<Integer> lines = new LinkedHashSet<>();
        for (ImportFinding finding : findings) {
            if (finding.isError()) {
                lines.add(finding.line());
            }
        }
        return lines.size();
    }
}
