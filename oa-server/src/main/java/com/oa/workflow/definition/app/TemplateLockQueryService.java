package com.oa.workflow.definition.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.LockedInstanceRow;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.LockedInstanceView;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * <b>在途实例锁版本查询</b>（{@code GET /flow-templates/{id}/locked-by}）—— AC-09 的可见性入口。
 *
 * <p>回答的是流程管理员最关心的那个问题：「我现在改模板 / 归档版本，会影响哪些还没走完的单据」。
 * 口径 = 「该模板版本下 {@code status = 'approving'} 的实例」：
 * <ul>
 *   <li><b>模板侧</b>：{@code flow_template} 是配置数据（无数据域），因此先用
 *       {@link FlowTemplateMapper#selectTemplateById} 定位版本号，未知 id 直接 404；</li>
 *   <li><b>实例侧</b>：{@code flow_instance} 是**受控表**，查询走
 *       {@link FlowInstanceMapper#selectInFlightByTemplate}（带 {@code @dataScope} 标记、
 *       Mapper 不继承 {@code BaseMapper}）并**在调用人的数据域下执行** —— 域外实例查不到，
 *       于是「哪些在途实例锁着这个版本」在不同管理员眼里天然只含他有权看见的那些；</li>
 *   <li><b>权限</b>：入口闸门取模板读权限 {@code admin:flow:template}
 *       （{@code WorkflowPermissionService#requireTemplateRead}），与模板列表 / 详情同源；
 *       数据域是第二层，两层都不省。</li>
 * </ul>
 *
 * <p>刻意**不**复用 {@code FlowRuntimeQueryService}：那边的出参是「以实例为主语」的运行态视图
 * （{@code /flow-instances/{id}/**}），本接口的主语是**模板版本**，按模板维度列出受影响实例。
 */
@Service
public class TemplateLockQueryService {

    private final FlowTemplateMapper templateMapper;
    private final FlowInstanceMapper instanceMapper;
    private final WorkflowPermissionService permissionService;

    public TemplateLockQueryService(FlowTemplateMapper templateMapper,
                                    FlowInstanceMapper instanceMapper,
                                    WorkflowPermissionService permissionService) {
        this.templateMapper = templateMapper;
        this.instanceMapper = instanceMapper;
        this.permissionService = permissionService;
    }

    /**
     * 该模板版本下**在途**（{@code approving}）的实例清单。
     *
     * @param templateId      模板版本 id（{@code flow_template.id}）
     * @param templateVersion 可选：显式指定版本号；为空则取 {@code templateId} 那一行的
     *                        {@code version}（即「这个版本」），二者同时进入 SQL 条件做一致性核对
     */
    public List<LockedInstanceView> lockedBy(Long templateId, Integer templateVersion) {
        permissionService.requireTemplateRead();
        if (templateId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "templateId 不能为空");
        }
        FlowTemplate template = templateMapper.selectTemplateById(templateId);
        if (template == null) {
            throw BizException.notFound("流程模板");
        }
        Integer version = templateVersion == null ? template.getVersion() : templateVersion;
        List<LockedInstanceRow> rows = instanceMapper.selectInFlightByTemplate(templateId, version);
        List<LockedInstanceView> views = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (LockedInstanceRow row : rows) {
                views.add(new LockedInstanceView(row.getInstanceId(), row.getBizNo(), row.getTemplateVersion(),
                        row.getStatus(), row.getInitiatorId(), row.getInitiatorName(),
                        row.getCurrentNodeSeq(), row.getCurrentNodeName(), row.getSubmittedAt()));
            }
        }
        return views;
    }
}
