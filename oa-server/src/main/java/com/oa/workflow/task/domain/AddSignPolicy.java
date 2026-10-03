package com.oa.workflow.task.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.json.JsonText;
import com.oa.workflow.runtime.domain.RuntimeEnums.AddSignType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 加签链的**编解码与判定**（2a.5）—— 落在 {@code flow_node_instance.add_sign_chain_json}
 * （doc/data-model.md §5.2），<b>不触碰</b> {@code flow_instance.approver_snapshot_json}
 * 的既有键名契约（快照是 2a.3 已固化的不可变文本；加签是运行时事实）。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §6.4 REQ-FLOW-003：「审批人可在审批时添加临时审批人（**前加签/后加签**）。
 *       前加签：加签人先审，审完回到本人；后加签：本人审完后加签人再审。
 *       <b>加签人必须签署意见</b>，加签行为记入审计日志」；</li>
 *   <li>doc/data-model.md §5.3：{@code flow_task.add_sign_type = pre|post}，
 *       {@code delegate_from = 发起加签/委托的原审批人}；</li>
 *   <li>doc/enums.md §6：{@code flow_task.status = added_sign}（本人动作被加签流程替代或已前加签）。</li>
 * </ul>
 *
 * <h2>链的相位（仅存于 {@code add_sign_chain_json}，不新增枚举、不改 DDL）</h2>
 * <table border="1">
 *   <tr><th>phase</th><th>含义</th><th>下一步</th></tr>
 *   <tr><td>{@code awaiting_delegate}（前加签）</td><td>加签人尚未处理</td>
 *       <td>加签人通过 → {@code done} + 为原审批人重新产生 pending 任务</td></tr>
 *   <tr><td>{@code awaiting_owner}（后加签）</td><td>本人已同意，等待加签人处理</td>
 *       <td>加签人通过 → {@code done} + 触发节点决议判定（本人那一票已计入）</td></tr>
 *   <tr><td>{@code done}</td><td>加签闭环完成</td><td>—</td></tr>
 * </table>
 *
 * <p><b>决议计数口径</b>：加签任务（{@code add_sign_type IS NOT NULL}）**不参与**阈值计数，
 * 「链未闭环」时引擎推迟节点决议（见 {@link #hasOpen(String)}）。
 *
 * <p>JSON 形状：{@code {"chain":[{order,type,from,to,delegate_task_id,phase,reason,created_at}, …]}}
 * —— 键名 snake_case，与本工程其余 JSON 列（快照 / 跳过条件）一致；旧数据若是裸数组会被自动包装。
 */
public final class AddSignPolicy {

    /** JSON 顶层键名（数组）。 */
    public static final String KEY_CHAIN = "chain";

    /** 相位：等待加签人（前加签）。 */
    public static final String PHASE_AWAITING_DELEGATE = "awaiting_delegate";

    /** 相位：等待加签人（后加签，本人已同意）。 */
    public static final String PHASE_AWAITING_OWNER = "awaiting_owner";

    /** 相位：闭环完成。 */
    public static final String PHASE_DONE = "done";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AddSignPolicy() {
    }

    /**
     * 链上的一条记录。
     *
     * @param order          追加顺序（1 起，等于该条在数组中的位置）
     * @param type           {@code pre} / {@code post}
     * @param from           发起加签的原审批人（写进 {@code flow_task.delegate_from}）
     * @param to             加签人
     * @param delegateTaskId 加签人那条任务的 id（生成后回填）
     * @param phase          相位（见类注释）
     * @param reason         加签原因（必填）
     * @param createdAt      记录时间
     */
    public record Entry(
            int order,
            String type,
            Long from,
            Long to,
            Long delegateTaskId,
            String phase,
            String reason,
            String createdAt
    ) {
    }

    /** 读链（无链 / 非法 JSON → 空列表；**不抛异常**：链损坏不应阻断审批）。 */
    public static List<Entry> read(String chainJson) {
        List<Entry> entries = new ArrayList<>();
        if (chainJson == null || chainJson.isBlank()) {
            return entries;
        }
        JsonNode root;
        try {
            root = JsonText.read(chainJson);
        } catch (IllegalArgumentException ex) {
            return entries;
        }
        if (root == null) {
            return entries;
        }
        JsonNode chain = root.isArray() ? root : root.get(KEY_CHAIN);
        if (chain == null || !chain.isArray()) {
            return entries;
        }
        int index = 0;
        for (JsonNode item : chain) {
            index++;
            if (item == null || !item.isObject()) {
                continue;
            }
            entries.add(new Entry(
                    item.path("order").asInt(index),
                    JsonText.text(item, "type"),
                    longOrNull(item, "from"),
                    longOrNull(item, "to"),
                    longOrNull(item, "delegate_task_id"),
                    JsonText.text(item, "phase"),
                    JsonText.text(item, "reason"),
                    JsonText.text(item, "created_at")));
        }
        return entries;
    }

    /**
     * 追加一条记录，返回**新的** JSON 文本。
     *
     * @param phase {@link #PHASE_AWAITING_DELEGATE}（前加签）或 {@link #PHASE_AWAITING_OWNER}（后加签）
     * @return 新的链 JSON（可直接写回 {@code flow_node_instance.add_sign_chain_json}）
     */
    public static String append(String chainJson, AddSignType type, Long from, Long to, Long delegateTaskId,
                                String phase, String reason) {
        ObjectNode wrapper = wrapper(chainJson);
        ArrayNode chain = chain(wrapper);
        ObjectNode item = JsonNodeFactory.instance.objectNode();
        item.put("order", chain.size() + 1);
        item.put("type", type == null ? null : type.code());
        if (from == null) {
            item.putNull("from");
        } else {
            item.put("from", from);
        }
        if (to == null) {
            item.putNull("to");
        } else {
            item.put("to", to);
        }
        if (delegateTaskId == null) {
            item.putNull("delegate_task_id");
        } else {
            item.put("delegate_task_id", delegateTaskId);
        }
        item.put("phase", phase);
        item.put("reason", reason);
        item.put("created_at", LocalDateTime.now().format(TIME));
        chain.add(item);
        return wrapper.toString();
    }

    /** 是否存在未闭环的加签记录（决定引擎是否推迟节点决议判定）。 */
    public static boolean hasOpen(String chainJson) {
        for (Entry entry : read(chainJson)) {
            if (!PHASE_DONE.equals(entry.phase())) {
                return true;
            }
        }
        return false;
    }

    /** 当前未闭环的那一条（异常数据多条并存时取最后一条）。 */
    public static Optional<Entry> openEntry(String chainJson) {
        Entry found = null;
        for (Entry entry : read(chainJson)) {
            if (!PHASE_DONE.equals(entry.phase())) {
                found = entry;
            }
        }
        return Optional.ofNullable(found);
    }

    /** 把某条加签任务对应的记录置为 {@code done}，返回新的 JSON 文本。 */
    public static String close(String chainJson, Long delegateTaskId) {
        ObjectNode wrapper = wrapper(chainJson);
        JsonNode chain = wrapper.get(KEY_CHAIN);
        if (chain == null || !chain.isArray()) {
            return wrapper.toString();
        }
        for (JsonNode item : chain) {
            if (item instanceof ObjectNode object) {
                Long id = longOrNull(object, "delegate_task_id");
                if (delegateTaskId != null && delegateTaskId.equals(id)) {
                    object.put("phase", PHASE_DONE);
                }
            }
        }
        return wrapper.toString();
    }

    /** 回填加签人任务的 id（任务生成后调用）。 */
    public static String bindTask(String chainJson, int order, Long delegateTaskId) {
        ObjectNode wrapper = wrapper(chainJson);
        JsonNode chain = wrapper.get(KEY_CHAIN);
        if (chain == null || !chain.isArray()) {
            return wrapper.toString();
        }
        for (JsonNode item : chain) {
            if (item instanceof ObjectNode object && object.path("order").asInt() == order) {
                if (delegateTaskId == null) {
                    object.putNull("delegate_task_id");
                } else {
                    object.put("delegate_task_id", delegateTaskId);
                }
            }
        }
        return wrapper.toString();
    }

    /**
     * 加签准入校验。
     *
     * @param allowAddSign 节点开关（快照 {@code allow_add_sign} / {@code flow_node.allow_add_sign}）
     * @param ownerId      发起加签的当前处理人
     * @param delegateId   加签人（**不得**是本人）
     */
    public static void assertAllowed(Boolean allowAddSign, Long ownerId, Long delegateId) {
        if (!Boolean.TRUE.equals(allowAddSign)) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "该节点未开启加签（flow_node.allow_add_sign = false），已拒绝（错误码 "
                            + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        if (delegateId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "加签必须指定加签人（delegateUserId）");
        }
        if (delegateId.equals(ownerId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能给自己加签");
        }
    }

    /** 加签类型解析（非法值给出可读文案）。 */
    public static AddSignType parseType(String code) {
        return AddSignType.of(code).orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID,
                "加签类型只支持 pre（前加签）或 post（后加签），实际「" + code + "」"));
    }

    /** 供测试/诊断使用：链的可读摘要。 */
    public static String describe(String chainJson) {
        List<Entry> entries = read(chainJson);
        if (entries.isEmpty()) {
            return "无加签记录";
        }
        StringBuilder builder = new StringBuilder("加签链（" + entries.size() + " 条）：");
        for (Entry entry : entries) {
            builder.append("\n  #").append(entry.order()).append(' ').append(entry.type())
                    .append(' ').append(entry.from()).append(" → ").append(entry.to())
                    .append(" phase=").append(entry.phase())
                    .append(" task=").append(entry.delegateTaskId())
                    .append(" reason=").append(entry.reason());
        }
        return builder.toString();
    }

    // ================================================================ 内部

    /** 取（或建）{@code {"chain":[...]}} 形态的包装对象。 */
    private static ObjectNode wrapper(String chainJson) {
        JsonNode root = null;
        if (chainJson != null && !chainJson.isBlank()) {
            try {
                root = JsonText.read(chainJson);
            } catch (IllegalArgumentException ignored) {
                root = null;
            }
        }
        if (root instanceof ObjectNode object) {
            ObjectNode copy = object.deepCopy();
            chain(copy);
            return copy;
        }
        ObjectNode created = JsonNodeFactory.instance.objectNode();
        created.set(KEY_CHAIN, root instanceof ArrayNode legacy ? legacy.deepCopy()
                : JsonNodeFactory.instance.arrayNode());
        return created;
    }

    /** 取（或建）{@code chain} 数组。 */
    private static ArrayNode chain(ObjectNode wrapper) {
        JsonNode existing = wrapper.get(KEY_CHAIN);
        if (existing instanceof ArrayNode array) {
            return array;
        }
        ArrayNode created = JsonNodeFactory.instance.arrayNode();
        wrapper.set(KEY_CHAIN, created);
        return created;
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isNumber() ? value.asLong() : parseLongOrNull(value.asText());
    }

    private static Long parseLongOrNull(String text) {
        try {
            return text == null ? null : Long.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
