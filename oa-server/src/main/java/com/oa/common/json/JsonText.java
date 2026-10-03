package com.oa.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 文本工具（**专用 ObjectMapper**，不受 Web 层序列化定制影响）。
 *
 * <h2>为什么不能直接用 Spring 的 {@code ObjectMapper}</h2>
 * <p>{@code JacksonConfig} 把 {@code Long} 注册为 {@code ToStringSerializer}
 * （主键防 JS 精度丢失，见 {@code common/config/JacksonConfig}）。这一口径只对**HTTP 出参**有意义；
 * 若拿它去序列化要**落库**的 JSON（{@code flow_node.approver_param}、
 * {@code flow_instance.approver_snapshot_json}），用户 id 会被写成字符串，
 * 与 {@code doc/data-model.md} §7.1 的快照结构与 {@code JSON_EXTRACT} 的口径漂移。
 * 因此本工具持有**独立**的 {@link ObjectMapper}：数字保持数字，时间由调用方自行格式化。
 *
 * <p>纯函数、无 Spring 依赖，可在单测中直接断言往返。
 */
public final class JsonText {

    /** 落库/解析共用的私有 mapper（不做任何类型定制）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonText() {
    }

    /** 解析为树（空白输入返回 {@code null}；非法 JSON 抛 {@link IllegalArgumentException}）。 */
    public static JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            return node == null || node.isNull() ? null : node;
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 解析失败：" + ex.getMessage());
        }
    }

    /** 任意对象 → JSON 文本（{@code null} → {@code null}，便于写入可空 JSON 列）。 */
    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            return node.isNull() ? null : node.toString();
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 序列化失败：" + ex.getMessage());
        }
    }

    /** JSON 文本 → 指定类型（空输入返回 {@code null}）。 */
    public static <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 反序列化失败（" + type.getSimpleName() + "）：" + ex.getMessage());
        }
    }

    /** 读文本字段（缺失/非文本 → {@code null}）。 */
    public static String text(JsonNode root, String field) {
        if (root == null || field == null) {
            return null;
        }
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    /** 从 JSON 文本里读数组字段为 {@code Long} 列表（缺省返回空列表）。 */
    public static List<Long> longArray(String json, String field) {
        List<Long> result = new ArrayList<>();
        JsonNode root = read(json);
        if (root == null) {
            return result;
        }
        JsonNode array = root.get(field);
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode item : array) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isNumber()) {
                result.add(item.asLong());
            } else {
                try {
                    result.add(Long.parseLong(item.asText().trim()));
                } catch (NumberFormatException ignored) {
                    // 非法项直接跳过：调用方据此走「无有效候选人」的拦截路径
                }
            }
        }
        return result;
    }

    /** 读数组字段为字符串列表。 */
    public static List<String> textArray(JsonNode root, String field) {
        List<String> result = new ArrayList<>();
        if (root == null || field == null) {
            return result;
        }
        JsonNode array = root.get(field);
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode item : array) {
            if (item != null && !item.isNull()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    /** 新建数组节点。 */
    public static ArrayNode array() {
        return JsonNodeFactory.instance.arrayNode();
    }

    /** 顺序敏感的 map（JSON 字段顺序即人类阅读顺序）。 */
    public static <V> Map<String, V> orderedMap() {
        return new LinkedHashMap<>();
    }
}
