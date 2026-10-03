package com.oa.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

/**
 * {@code AuditAspect} 的 JSON 列合法性回归测试（任务 1）。
 *
 * <h2>回归背景（运行期实测）</h2>
 * <p>旧实现在异常路径用**字符串拼接**产出 {@code after_json}：
 * {@code "{\"error\":\"" + simpleName + "\",\"message\":\"" + message.replace("\"", "'") + "\"}"}。
 * 只处理了双引号，**没有**处理换行 / 制表符 / 反斜杠——而这三者正是 MyBatis、JDBC、
 * {@code NestedServletException} 消息里最常见的字符。MySQL 的 {@code sys_log.after_json} 是
 * {@code JSON} 列，实测直接拒绝：
 * <pre>
 * ERROR 3140 (22032): Invalid JSON text: "Invalid encoding in string."
 *   at position 44 in value for column 'sys_log.after_json'
 * </pre>
 * 本测试把「旧拼装非法 / 新摘要合法」两侧都断言下来，避免回退。
 */
class AuditAspectErrorJsonTest {

    /** 业务异常 message 里同时含换行、制表符、反斜杠、双引号与中文（旧实现的三个死穴）。 */
    private static final String NASTY_MESSAGE =
            "第一行 SQL 失败\n第二行\t原因：字段 \"amount\" 非法，路径 C:\\temp\\x";

    @AfterEach
    void tearDown() {
        MDC.remove("traceId");
    }

    /** 供反射取 {@link Audited} 的样例（等价于真实控制器方法）。 */
    static class Sample {

        @Audited(action = "sample_ok", targetType = "sample", targetId = "#id", recordArgs = true)
        public Map<String, Object> ok(Long id) {
            return Map.of("id", id, "ok", true);
        }

        @Audited(action = "sample_boom", targetType = "sample", targetId = "#id", recordArgs = true)
        public Map<String, Object> boom(Long id) {
            throw new BizException(ErrorCode.CONFLICT, NASTY_MESSAGE);
        }
    }

    private static AuditAspect aspect(AuditLogWriter writer) {
        return new AuditAspect(writer, new ObjectMapper());
    }

    private static ProceedingJoinPoint joinPointFor(String method, boolean throwing) throws Throwable {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(new String[]{"id"});
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L});
        if (throwing) {
            when(joinPoint.proceed()).thenThrow(new BizException(ErrorCode.CONFLICT, NASTY_MESSAGE));
        }
        return joinPoint;
    }

    private static Audited auditedOf(String method) throws NoSuchMethodException {
        return Sample.class.getMethod(method, Long.class).getAnnotation(Audited.class);
    }

    /** JSON 合法性判据 ①：能被**严格** JSON 解析器读回（Jackson 默认拒绝未转义控制字符）。 */
    private static void assertStrictJson(String json) {
        try {
            new ObjectMapper().readTree(json);
        } catch (Exception ex) {
            throw new AssertionError("after_json 不是合法 JSON：" + ex.getMessage() + "\n原文=" + json, ex);
        }
        // JSON 合法性判据 ②：不得含**裸控制字符**（< 0x20）——这正是 MySQL JSON 列拒收的东西
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            assertThat(ch)
                    .as("after_json 第 %d 位出现裸控制字符 U+%04X（MySQL JSON 列会报 Invalid JSON text）",
                            i, (int) ch)
                    .isGreaterThanOrEqualTo(' ');
        }
    }

    @Test
    @DisplayName("异常路径：after_json 是合法 JSON，且含异常类型 / message / traceId（含换行反斜杠也不炸）")
    void errorPathProducesValidJson() throws Throwable {
        MDC.put("traceId", "trace-20261003-abcd");

        AuditLogWriter writer = mock(AuditLogWriter.class);
        AuditAspect aspect = aspect(writer);

        assertThatThrownBy(() -> aspect.around(joinPointFor("boom", true), auditedOf("boom")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("第一行 SQL 失败");

        ArgumentCaptor<AuditLogWriter.AuditRecord> captor = ArgumentCaptor.forClass(AuditLogWriter.AuditRecord.class);
        verify(writer).append(captor.capture());
        String afterJson = captor.getValue().afterJson();

        assertStrictJson(afterJson);
        assertThat(afterJson)
                .as("异常类型 + message + traceId 三者必须都在结构化摘要里")
                .contains("\"error\":\"BizException\"")
                .contains("\"traceId\":\"trace-20261003-abcd\"")
                .contains("第一行 SQL 失败")
                .contains("amount")
                .contains("C:\\\\temp");
        assertThat(afterJson)
                .as("换行与制表符必须以 JSON 转义序列出现，而不是裸字符")
                .contains("\\n")
                .contains("\\t");
        assertThat(afterJson)
                .as("堆栈只作为 JSON **字符串字段**（stack），不是被拼成 JSON 结构")
                .contains("\"stack\":\"")
                .contains("\"errorType\":\"com.oa.common.error.BizException\"");
        assertThat(captor.getValue().action()).isEqualTo("sample_boom");
        assertThat(captor.getValue().beforeJson()).as("入参 JSON 同样由 Jackson 产出，天然合法").isNotNull();
        assertStrictJson(captor.getValue().beforeJson());
    }

    @Test
    @DisplayName("对照：旧的字符串拼接产物在严格解析器下**不是**合法 JSON（回归护栏）")
    void oldStyleConcatenationWasInvalid() {
        String legacy = "{\"error\":\"" + BizException.class.getSimpleName() + "\",\"message\":\""
                + NASTY_MESSAGE.replace("\"", "'") + "\"}";
        assertThatThrownBy(() -> new ObjectMapper().readTree(legacy))
                .as("旧拼装必须被判为非法，否则本回归测试失去意义")
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("正常路径：after_json 仍是业务返回体的序列化结果（行为不变）")
    void successPathUnchanged() throws Throwable {
        AuditLogWriter writer = mock(AuditLogWriter.class);
        AuditAspect aspect = aspect(writer);
        Method method = Sample.class.getMethod("ok", Long.class);
        Audited audited = method.getAnnotation(Audited.class);

        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(new String[]{"id"});
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L});
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", 7L);
        result.put("ok", true);
        when(joinPoint.proceed()).thenReturn(result);

        aspect.around(joinPoint, audited);

        ArgumentCaptor<AuditLogWriter.AuditRecord> captor = ArgumentCaptor.forClass(AuditLogWriter.AuditRecord.class);
        verify(writer).append(captor.capture());
        assertThat(captor.getValue().afterJson()).isEqualTo("{\"id\":7,\"ok\":true}");
        assertThat(captor.getValue().afterJson())
                .as("正常路径不得出现异常摘要字段")
                .doesNotContain("\"error\"");
    }

    @Test
    @DisplayName("超长返回体：after_json 退化为合法 JSON 包装，而不是被切断")
    void oversizedAfterJsonStaysValid() throws Throwable {
        AuditLogWriter writer = mock(AuditLogWriter.class);
        AuditAspect aspect = aspect(writer);
        Method method = Sample.class.getMethod("ok", Long.class);
        Audited audited = method.getAnnotation(Audited.class);

        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(new String[]{"id"});
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L});
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blob", "x".repeat(9000));
        when(joinPoint.proceed()).thenReturn(result);

        aspect.around(joinPoint, audited);

        ArgumentCaptor<AuditLogWriter.AuditRecord> captor = ArgumentCaptor.forClass(AuditLogWriter.AuditRecord.class);
        verify(writer).append(captor.capture());
        String afterJson = captor.getValue().afterJson();
        assertStrictJson(afterJson);
        assertThat(afterJson).contains("\"truncated\":true").hasSizeLessThan(4200);
    }

    /**
     * 超长返回体的 {@code preview} <b>不得在代理对中间截断</b>。
     *
     * <h2>回归背景</h2>
     * <p>旧实现直接 {@code json.substring(0, 4000 - 80)}：截断点若落在某个**增补平面字符**
     * （emoji / 生僻字，UTF-16 占两个 {@code char}）的高位与低位之间，预览就以**孤立高代理**结尾 ——
     * JDBC 把该字符编码成 {@code ?}（字符被换掉），且 {@code preview} 不再是原文的逐字符前缀。
     * 属低风险但非零：任何把 emoji 放进返回体的接口都可能命中。
     *
     * <h2>为什么这样写</h2>
     * <p>不把「3920」这类内部常量抄进测试：先用**纯 ASCII 探针**量出实际截断点，再据此把 emoji 的
     * 高代理**精确摆到该位置**，并先断言「朴素截断确实会切断代理对」—— 否则用例可能空跑而不自知。
     */
    @Test
    @DisplayName("超长返回体：preview 避开代理对（emoji / 增补平面字符不留孤立代理），且仍是合法 JSON")
    void oversizedPreviewNeverSplitsSurrogatePair() throws Throwable {
        // ① 纯 ASCII 探针：量出内部截断点（ASCII 输入下 preview 长度 == 截断点，不会触发回退）
        String asciiJson = afterJsonOf(Map.of("blob", "a".repeat(9000)));
        assertStrictJson(asciiJson);
        int boundary = previewText(asciiJson).length();
        assertThat(boundary).as("超长返回体必须退化为 truncated 包装").isPositive().isLessThan(4000);

        // ② 序列化后的形状是 {"blob":"…"}：量出 payload 在 JSON 里的起点，才能把高代理摆到 boundary-1
        int prefix = new ObjectMapper().writeValueAsString(Map.of("blob", "a")).indexOf('a');
        assertThat(prefix).isPositive();

        String emoji = "😀";                       // U+1F600 —— UTF-16 是 D83D DE00（一对代理）
        String payload = "a".repeat(boundary - prefix - 1) + emoji + "b".repeat(200);
        String naiveJson = new ObjectMapper().writeValueAsString(Map.of("blob", payload));
        assertThat(Character.isHighSurrogate(naiveJson.charAt(boundary - 1)))
                .as("测试前提：朴素截断点必须落在 emoji 的高代理之后，否则本用例覆盖不到缺陷")
                .isTrue();

        // ③ 真实路径：末位是高代理 ⇒ 回退一位（宁可少一个字符，也不写出孤立代理）
        String afterJson = afterJsonOf(Map.of("blob", payload));
        assertStrictJson(afterJson);
        String preview = previewText(afterJson);
        assertThat(preview)
                .as("回退一位后 preview 恰好比朴素截断点少一个字符")
                .hasSize(boundary - 1);
        assertThat(preview).as("emoji 整体不应出现在预览里（高低位不得被拆开）").doesNotContain(emoji);
        assertThat(preview.charAt(preview.length() - 1)).isEqualTo('a');
        assertThat(hasUnpairedSurrogate(preview)).as("preview 不得以孤立代理字符结尾").isFalse();

        // ④ 混合样本（多组 emoji + U+1D11E 音乐符号）：任何位置都不得留下孤立代理
        String mixed = "x".repeat(boundary - prefix - 40) + emoji.repeat(20) + "𝄞".repeat(20) + "y".repeat(500);
        String rawMixed = new ObjectMapper().writeValueAsString(Map.of("blob", mixed));
        String mixedJson = afterJsonOf(Map.of("blob", mixed));
        assertStrictJson(mixedJson);
        String mixedPreview = previewText(mixedJson);
        assertThat(hasUnpairedSurrogate(mixedPreview)).as("混合增补平面字符样本同样不得留下孤立代理").isFalse();
        // 预览是**转义后的** JSON 前缀：去掉包装引号的转义后，必须逐字符等于原文前缀
        String unescaped = mixedPreview.replace("\\\"", "\"");
        assertThat(unescaped).isEqualTo(rawMixed.substring(0, unescaped.length()));
    }

    // ================================================================ 代理对相关工具

    /** 跑一次正常路径（{@code ok}）的审计写入，取回 {@code after_json}。 */
    private static String afterJsonOf(Object result) throws Throwable {
        AuditLogWriter writer = mock(AuditLogWriter.class);
        AuditAspect aspect = aspect(writer);

        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(new String[]{"id"});
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L});
        when(joinPoint.proceed()).thenReturn(result);

        aspect.around(joinPoint, auditedOf("ok"));

        ArgumentCaptor<AuditLogWriter.AuditRecord> captor = ArgumentCaptor.forClass(AuditLogWriter.AuditRecord.class);
        verify(writer).append(captor.capture());
        return captor.getValue().afterJson();
    }

    /** 取出 {@code {"truncated":true,…,"preview":"…"}} 里的 preview 文本。 */
    private static String previewText(String afterJson) throws Exception {
        JsonNode node = new ObjectMapper().readTree(afterJson).path("preview");
        assertThat(node.isTextual()).as("超长返回体必须带 preview 字符串").isTrue();
        return node.asText();
    }

    /** 是否存在**孤立代理**（高代理后不跟低代理，或出现无高位配对的低代理）。 */
    private static boolean hasUnpairedSurrogate(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    return true;
                }
                i++;                       // 合法的代理对：整对跳过
            } else if (Character.isLowSurrogate(ch)) {
                return true;               // 低代理前面没有高位
            }
        }
        return false;
    }
}
