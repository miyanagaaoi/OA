package com.oa.form.template.writemodel;

import com.oa.authz.visibility.FormFieldWriteGuard;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import com.oa.form.app.FormWritePolicy;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.runtime.infra.FlowNodeInstanceMapper;
import com.oa.workflow.runtime.infra.row.FlowNodeInstanceRow;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * <b>2b.2 三态读写模型的服务端强制点</b> —— 把「谁在什么状态下能写哪些字段」判到位。
 *
 * <h2>三层各管什么（缺一不可）</h2>
 * <ol>
 *   <li><b>状态层</b>：{@link FormWritePolicy}（纯逻辑）——草稿全可写 / 审批中全只读 /
 *       待补件仅**附件类字段（{@code type ∈ {file, files}}）**+ {@code supplement_note} / 已完结只读；
 *       印鉴单 {@code return_status}/{@code return_date} 是**唯一例外**且**仅在审批中**；
 *       {doc/forms.md} §1.2 / §5 / §7；</li>
 *   <li><b>角色层</b>：{@link FormFieldWriteGuard}（1.6 既有实现）——金额字段对非财务角色只读（40306），
 *       与状态层**正交**：状态允许写 ≠ 角色允许写金额；</li>
 *   <li><b>本类</b>：把「上下文事实」查出来（状态、是否发起人、是否节点⑦候选人、字段全集），
 *       然后一次性调用上面两层。**调用方不得自行拼装**这两条规则 —— 各处手写正是漏洞来源。</li>
 * </ol>
 *
 * <h2>为什么必须服务端判（真源原文）</h2>
 * <p>{@code doc/forms.md} §1.2 末段：「实现要求：服务端必须按状态白名单校验可写字段，
 * <b>不能仅依赖前端置灰</b>。见 PRD REQ-FLOW-023 与 AC-28」；
 * {@code doc/templates.md} §2.5「状态白名单」行同义。
 *
 * <h2>节点⑦候选人的判定口径</h2>
 * <p>{@code doc/forms.md} §5 例外边界表：「审批中 | **发起人（归还登记）**、**节点⑦归档登记人** |
 * 仅 {@code return_status} / {@code return_date}」。这里「节点⑦归档登记人」=
 * <b>实例当前节点序号落在 {@code archive_register} 那个节点实例上、且调用人在该节点实例的
 * {@code approver_ids_json} 内</b>（节点码真源：{@code doc/enums.md} §2 的
 * {@code archive_register} = ⑦归档登记）。<b>不看角色名</b>：
 * {@code templates.md} §1.1 的 ⑦ 由 {@code role_code=admin} 解析成候选人快照，
 * 用角色名判会把「管理员身份的其他人」也放进写白名单。
 */
@Service
public class FormStateWriteGuard {

    private static final Logger log = LoggerFactory.getLogger(FormStateWriteGuard.class);

    /** 节点⑦节点码（{@code doc/enums.md} §2）。 */
    public static final String ARCHIVE_NODE_CODE = "archive_register";

    private final FormFieldWriteGuard fieldWriteGuard;
    private final FlowNodeInstanceMapper nodeInstanceMapper;

    public FormStateWriteGuard(FormFieldWriteGuard fieldWriteGuard, FlowNodeInstanceMapper nodeInstanceMapper) {
        this.fieldWriteGuard = fieldWriteGuard;
        this.nodeInstanceMapper = nodeInstanceMapper;
    }

    // ================================================================ 上下文

    /**
     * 计算写入上下文（**读库**：节点实例用于判定节点⑦身份）。
     *
     * <p>不传 schema 字段类型信息 ⇒ 待补件窗口退化为字段码清单
     * （{@code attachments} + {@code supplement_note}）。**表单/附件路径请改用 4 参重载**
     * 并传入 {@code FormSchema#attachmentFieldCodes()}。
     *
     * @param instance  实例行（已过数据域，域外调用方拿不到）
     * @param principal 当前登录人（为 {@code null} 时不做身份例外，仍按状态白名单判）
     * @param allFields schema 字段全集
     */
    public WriteContext contextOf(FlowInstanceRow instance, CurrentUser principal, Set<String> allFields) {
        return contextOf(instance, principal, allFields, Set.of());
    }

    /**
     * 计算写入上下文（**读库**；含 schema 附件类字段类型信息）。
     *
     * @param attachmentFields schema 里 {@code type ∈ {file, files}} 的字段码
     *                         （{@code FormSchema#attachmentFieldCodes()}）——待补件窗口按**字段类型**
     *                         放行全部附件类字段（合同单的 {@code counterparty_docs} 等同理）
     */
    public WriteContext contextOf(FlowInstanceRow instance, CurrentUser principal, Set<String> allFields,
                                  Set<String> attachmentFields) {
        List<FlowNodeInstanceRow> nodes = loadNodes(instance);
        return resolve(instance, principal, allFields, nodes, attachmentFields);
    }

    /**
     * 纯函数版本（不读库；单测直接喂节点实例清单）。
     */
    public WriteContext resolve(FlowInstanceRow instance, CurrentUser principal, Set<String> allFields,
                               List<FlowNodeInstanceRow> nodes) {
        return resolve(instance, principal, allFields, nodes, Set.of());
    }

    /**
     * 纯函数版本（不读库；含 schema 附件类字段类型信息）。
     */
    public WriteContext resolve(FlowInstanceRow instance, CurrentUser principal, Set<String> allFields,
                               List<FlowNodeInstanceRow> nodes, Set<String> attachmentFields) {
        FormWritePolicy.FormState state = FormWritePolicy.resolveState(
                instance == null ? null : instance.getStatus(),
                instance == null ? null : instance.getSubStatus());
        FormWritePolicy.FormType formType = FormWritePolicy.FormType.of(
                instance == null ? null : instance.getFormType());
        boolean isInitiator = instance != null && principal != null && principal.id() != null
                && principal.id().equals(instance.getInitiatorId());
        boolean isArchiveNode = isArchiveNodeActor(instance, principal, nodes);
        return WriteContext.of(state, formType, isInitiator, isArchiveNode, allFields, attachmentFields);
    }

    private List<FlowNodeInstanceRow> loadNodes(FlowInstanceRow instance) {
        if (instance == null || instance.getId() == null || nodeInstanceMapper == null) {
            return List.of();
        }
        try {
            return nodeInstanceMapper.selectByInstance(instance.getId());
        } catch (RuntimeException ex) {
            log.warn("读取实例 {} 的节点实例失败，节点⑦（归档登记）身份例外按不成立处理：{}",
                    instance.getId(), ex.getMessage());
            return List.of();
        }
    }

    /**
     * 调用人是否是节点⑦（归档登记）的候选人。
     *
     * <p>判定三要素：① 实例当前节点序号非空且落在某条 {@code archive_register} 节点实例上；
     * ② 该节点实例的状态**未终态**（{@code cancelled}/{@code skipped}/{@code rejected} 之外）；
     * ③ 调用人 id 在该节点实例的 {@code approver_ids_json} 内。
     */
    public static boolean isArchiveNodeActor(FlowInstanceRow instance, CurrentUser principal,
                                             List<FlowNodeInstanceRow> nodes) {
        if (instance == null || principal == null || principal.id() == null
                || instance.getCurrentNodeSeq() == null || nodes == null || nodes.isEmpty()) {
            return false;
        }
        for (FlowNodeInstanceRow node : nodes) {
            if (node == null || !ARCHIVE_NODE_CODE.equals(node.getNodeCode())) {
                continue;
            }
            if (node.getNodeSeq() != null && !node.getNodeSeq().equals(instance.getCurrentNodeSeq())) {
                continue;
            }
            String status = node.getStatus() == null ? "" : node.getStatus().trim().toLowerCase(java.util.Locale.ROOT);
            if ("cancelled".equals(status) || "skipped".equals(status) || "rejected".equals(status)) {
                continue;
            }
            if (containsApprover(node.getApproverIdsJson(), principal.id())) {
                return true;
            }
        }
        return false;
    }

    /** {@code approver_ids_json} 是否含该用户 id（支持 {@code [1,2]} / {@code {"userIds":[1,2]}} 两种形状）。 */
    public static boolean containsApprover(String approverIdsJson, Long userId) {
        if (approverIdsJson == null || approverIdsJson.isBlank() || userId == null) {
            return false;
        }
        String needle = userId.toString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+").matcher(approverIdsJson);
        while (matcher.find()) {
            if (needle.equals(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ 断言

    /**
     * 严格断言：载荷里任一字段不可写即**整单**拒绝（403 {@link ErrorCode#FIELD_WRITE_DENIED}，
     * 或金额角色规则命中时 403 {@link ErrorCode#AMOUNT_READ_ONLY}）。
     *
     * <p>为什么整单拒而不是部分写入：AC-28 的越权形态是「前端篡改请求」——
     * 若只剥掉越权字段、照写其余，客户端会收到 200 并以为整份载荷已生效（状态不一致的静默缺陷）。
     */
    public void assertWritable(Map<String, Object> payload, WriteContext context) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        Set<String> fields = new LinkedHashSet<>(payload.keySet());
        // 角色层（金额只读，40306）先判：它与状态无关，草稿态同样拒绝（PRD §5.3）
        fieldWriteGuard.assertAmountWritable(fields);
        assertStateWritable(fields, context);
    }

    /**
     * 仅状态层的断言（角色层已另行判定时使用）。
     *
     * @throws BizException 403 {@link ErrorCode#FIELD_WRITE_DENIED}
     */
    public void assertStateWritable(Set<String> fields, WriteContext context) {
        if (fields == null || fields.isEmpty() || context == null) {
            return;
        }
        for (String field : fields) {
            if (!context.writable(field)) {
                throw deny(field, context);
            }
        }
    }

    /** 宽容过滤：剥掉不可写字段（增量保存口径；**不用于**提交动作）。 */
    public Map<String, Object> filterWritable(Map<String, Object> payload, WriteContext context) {
        return fieldWriteGuard.filter(payload, context.state(), context.formType(),
                context.isInitiator(), context.isArchiveNode(), context.allFields(), context.attachmentFields());
    }

    private static BizException deny(String field, WriteContext context) {
        String extra = "";
        if (context.state() == FormWritePolicy.FormState.PENDING_SUPPLEMENT) {
            extra = "（待补件期仅附件类字段（type=file/files）与补件说明可写；改主字段请走「驳回 → 修改 → 重新提交」）";
        } else if (context.state() == FormWritePolicy.FormState.APPROVING) {
            extra = "（审批中主字段一律只读；印鉴单归还状态/日期是唯一例外，且仅发起人与节点⑦可改）";
        } else if (context.state() == FormWritePolicy.FormState.CLOSED) {
            extra = "（已完结单据只读）";
        }
        return new BizException(ErrorCode.FIELD_WRITE_DENIED,
                String.format(ErrorCode.FIELD_WRITE_DENIED.getMessage(), field) + extra)
                .withDetail("field", field)
                .withDetail("state", context.state().name())
                .withDetail("formType", context.formType().name())
                .withDetail("writableFields", context.writableFields());
    }
}
