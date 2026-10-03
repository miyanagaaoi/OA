package com.oa.form;

import com.oa.form.template.schema.FormSchema;
import com.oa.form.template.schema.FormSchemaParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;

/**
 * <b>从真源种子脚本里取出四类单据的 {@code form_schema_json}</b>（测试夹具）。
 *
 * <p>为什么不用「测试里另写一份 schema」：那样测的是「我写的那份」，而不是
 * 「真正会入库、真正会驱动渲染与校验的那份」。本夹具直接解析
 * {@code oa-deploy/sql/03-templates.sql}（= 真源 {@code doc/templates.md} §2 的落地物，
 * 由 {@code tools/check-templates-sql.js} 与真源对账）里的 {@code INSERT} 字面量，
 * 因此解析器/校验器的单测对象与生产完全同源。
 */
public final class FormSchemaFixtures {

    /** 四条模板 INSERT 里 JSON 字面量后紧跟的发布日期（用于把 JSON 与 SQL 的其余文本分开）。 */
    private static final Pattern SCHEMA_LITERAL = Pattern.compile("'(\\{[\\s\\S]*?\\})',\\s*'20\\d\\d-\\d\\d-\\d\\d");

    private static final List<Path> CANDIDATES = List.of(
            Path.of("..", "oa-deploy", "sql", "03-templates.sql"),
            Path.of("oa-deploy", "sql", "03-templates.sql"),
            Path.of("..", "..", "oa-deploy", "sql", "03-templates.sql"));

    private FormSchemaFixtures() {
    }

    private static Path scriptPath() {
        for (Path candidate : CANDIDATES) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        Assumptions.assumeTrue(false, "找不到 oa-deploy/sql/03-templates.sql，跳过与真源同源的 schema 用例");
        throw new IllegalStateException("unreachable");
    }

    /** {@code form_type → form_schema_json 原文}（四个）。 */
    public static Map<String, String> rawSchemas() {
        String sql;
        try {
            sql = Files.readString(scriptPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 03-templates.sql 失败：" + ex.getMessage(), ex);
        }
        Map<String, String> raw = new LinkedHashMap<>();
        Matcher matcher = SCHEMA_LITERAL.matcher(sql);
        while (matcher.find()) {
            String json = matcher.group(1);
            Matcher formType = Pattern.compile("\"form_type\"\\s*:\\s*\"([a-z]+)\"").matcher(json);
            if (formType.find()) {
                raw.put(formType.group(1), json);
            }
        }
        return raw;
    }

    /** 解析后的四类 schema（键 = {@code form_type}）。 */
    public static Map<String, FormSchema> schemas() {
        Map<String, FormSchema> schemas = new LinkedHashMap<>();
        rawSchemas().forEach((formType, json) -> schemas.put(formType,
                FormSchemaParser.parse(json, formType, 1)));
        return schemas;
    }

    /** 取单个 schema（缺失即让用例失败，避免静默跳过）。 */
    public static FormSchema schema(String formType) {
        FormSchema schema = schemas().get(formType);
        if (schema == null) {
            throw new IllegalStateException("03-templates.sql 中缺少 " + formType + " 的 form_schema_json");
        }
        return schema;
    }
}
