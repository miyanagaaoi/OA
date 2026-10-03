package com.oa.form.template.schema;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.TemplateStatus;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * <b>2b.1 表单模板引擎的取数入口</b>：按「哪个版本」提供 {@code form_schema_json}。
 *
 * <h2>三条取数口径（互不替代）</h2>
 * <table>
 *   <tr><th>场景</th><th>方法</th><th>版本来源</th><th>依据</th></tr>
 *   <tr>
 *     <td><b>在途/已办单据的渲染与校验（权威口径）</b></td>
 *     <td>{@link #forInstance(FlowInstanceRow)}</td>
 *     <td>{@code flow_instance.template_id}（**发起时锁定**）</td>
 *     <td>doc/templates.md §3.2 V-02 + AC-09</td>
 *   </tr>
 *   <tr>
 *     <td>取某模板某版本的表单 schema</td>
 *     <td>{@link #forTemplate(Long, Integer)}</td>
 *     <td>显式 {@code templateId}</td>
 *     <td>doc/templates.md §2.1 / §3.1「模板版本」</td>
 *   </tr>
 *   <tr>
 *     <td>新建单据（发起页）</td>
 *     <td>{@link #publishedFor(String)}</td>
 *     <td>同 {@code code} 下唯一的 {@code published} 版本</td>
 *     <td>doc/templates.md §3.3「published 是 否可被新实例使用＝是」</td>
 *   </tr>
 * </table>
 *
 * <h2>为什么不能只留一个方法（AC-09 的关键）</h2>
 * <p>「取当前 published 的 schema」如果也被用在**在途单据**上，管理员一次发版就会让在途单据的
 * 字段集与校验规则跟着变 —— 直接违反 {@code doc/templates.md} §3.2 V-02 与 AC-09
 * （「单据A的**剩余节点仍按 v1 执行**」；表单侧同理见 V-03「提交时固化字段定义与值」）。
 * 因此 {@link #forInstance} 与 {@link #publishedFor} 是两个**语义不同**的方法，不能合并。
 */
@Service
public class FormSchemaService {

    private static final Logger log = LoggerFactory.getLogger(FormSchemaService.class);

    private final FlowTemplateMapper templateMapper;

    public FormSchemaService(FlowTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    /**
     * <b>实例锁定版本</b>的 schema（AC-09）：按 {@code flow_instance.template_id} 取模板行。
     *
     * <p>若该行的 {@code version} 与 {@code flow_instance.template_version} 不一致，打 WARN 并**以实例
     * 锁定的 {@code template_version} 为准**（解析会因此失败 → 40008）：宁可显式失败，
     * 也不静默渲染成另一个版本的字段集。
     */
    public FormSchema forInstance(FlowInstanceRow instance) {
        if (instance == null || instance.getTemplateId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "实例缺少 template_id，无法解析表单模板");
        }
        FlowTemplate template = templateMapper.selectTemplateById(instance.getTemplateId());
        if (template == null) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    String.format("实例 %s 锁定的模板 id=%s 不存在（doc/templates.md §3.2 V-02：实例按发起时版本执行）",
                            instance.getId(), instance.getTemplateId()))
                    .withDetail("instanceId", instance.getId())
                    .withDetail("templateId", instance.getTemplateId());
        }
        Integer lockedVersion = instance.getTemplateVersion() == null
                ? template.getVersion() : instance.getTemplateVersion();
        if (template.getVersion() != null && !template.getVersion().equals(lockedVersion)) {
            log.warn("实例 {} 锁定版本 v{} 与模板行 id={} 当前版本 v{} 不一致，按锁定版本 v{} 解析 schema",
                    instance.getId(), lockedVersion, template.getId(), template.getVersion(), lockedVersion);
        }
        return FormSchemaParser.parse(template.getFormSchemaJson(),
                instance.getFormType() == null ? template.getFormType() : instance.getFormType(), lockedVersion);
    }

    /** 按 {@code templateId}（可指定 {@code version}）取 schema。 */
    public FormSchema forTemplate(Long templateId, Integer version) {
        if (templateId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "templateId 不能为空");
        }
        FlowTemplate template = templateMapper.selectTemplateById(templateId);
        if (template == null) {
            throw BizException.notFound("流程模板#" + templateId);
        }
        if (version != null && template.getVersion() != null && !version.equals(template.getVersion())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    String.format("模板 id=%s 的当前版本为 v%s，与请求的 v%s 不一致"
                                    + "（请用 /flow-templates/{code}/versions/{version} 口径按版本取数）",
                            templateId, template.getVersion(), version))
                    .withDetail("templateId", templateId)
                    .withDetail("requestedVersion", version);
        }
        return FormSchemaParser.parse(template.getFormSchemaJson(), template.getFormType(), template.getVersion());
    }

    /** 按 {@code (code, version)} 取 schema（版本历史的精确取数口径，templates.md §3.2 V-01）。 */
    public FormSchema forCodeAndVersion(String code, Integer version) {
        String normalized = FormSchemaParser.requireKnownFormType(code);
        if (version == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "version 不能为空");
        }
        FlowTemplate template = templateMapper.selectByCodeAndVersion(normalized, version);
        if (template == null) {
            throw BizException.notFound(String.format("单据类型 %s 的模板 v%d", normalized, version));
        }
        return FormSchemaParser.parse(template.getFormSchemaJson(), template.getFormType(), template.getVersion());
    }

    /**
     * 新建单据口径：该单据类型**当前已发布**版本的 schema。
     *
     * @throws BizException 40008 无已发布版本 / schema 非法
     */
    public FormSchema publishedFor(String formType) {
        String normalized = FormSchemaParser.requireKnownFormType(formType);
        List<FlowTemplate> published = templateMapper.selectByCodeAndStatus(normalized,
                TemplateStatus.PUBLISHED.code());
        if (published.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    String.format("单据类型「%s」没有已发布的流程/表单模板版本，无法发起"
                            + "（doc/templates.md §3.3：draft 与 archived 版本不可被新实例使用）", normalized))
                    .withDetail("formType", normalized);
        }
        FlowTemplate template = published.get(0);
        return FormSchemaParser.parse(template.getFormSchemaJson(), template.getFormType(), template.getVersion());
    }

    /** 该单据类型当前已发布的模板行（建草稿链路要它的 id/version 去锁版本）。 */
    public FlowTemplate publishedTemplate(String formType) {
        String normalized = FormSchemaParser.requireKnownFormType(formType);
        List<FlowTemplate> published = templateMapper.selectByCodeAndStatus(normalized,
                TemplateStatus.PUBLISHED.code());
        if (published.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    String.format("单据类型「%s」没有已发布的流程/表单模板版本", normalized));
        }
        return published.get(0);
    }
}
