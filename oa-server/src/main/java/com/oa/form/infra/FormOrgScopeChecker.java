package com.oa.form.infra;

import com.oa.form.template.validate.OrgScopeChecker;
import com.oa.identity.app.OrgHierarchy;
import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.OrgNodeView;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@link OrgScopeChecker} 的生产实现：{@code rules[orgScope] = initiator_company_subtree}
 * 的「发起人公司及以下节点」判定。
 *
 * <h2>为什么不另写一份组织树 SQL</h2>
 * <p>组织节点的「存在 / 启用 / 祖先路径」在系统里**已经有唯一查询口径**（
 * {@link ApproverDirectory#org(Long)}，其 {@code @dataScope} 标记语句在
 * {@link ApproverDirectory} 的文档约定下由 {@code JdbcApproverDirectory} 统一包系统口径），
 * 再写一套只会多一处会漂移的真相 —— 与 {@code FormPickerDirectoryChecker} 同一取舍。
 *
 * <h2>子树判定用既有纯函数</h2>
 * <p>{@link OrgHierarchy#isDescendantOrSelf(String, String)}（{@code path} 前缀匹配）是
 * 「子树含自身」的**唯一口径**（{@code doc/data-model.md} §2.1 的 {@code path} 注释
 * 「祖先路径 {@code /1/12/135/}，含自身」）。{@code path} 归一化后带首尾斜杠，
 * 因此 {@code /1/120/} 不会被误判成 {@code /1/12/} 的后代。
 *
 * <h2>失败即「不在范围内」（fail-closed）</h2>
 * <ul>
 *   <li>非十进制取值（符号型占位 {@code initiator_company} / 人手填的姓名）→ 不在范围内；</li>
 *   <li>节点不存在或已停用（{@code status <> active}）→ 不在范围内；</li>
 *   <li>公司 id 为空或公司节点查不到（集团层账号未挂公司）→ 不在范围内
 *       （范围无法判定时**拒绝**，不静默放行）。</li>
 * </ul>
 * <p>前两条与 {@code pickerValue}（元素存在性）的口径**同源**，因此校验器只会对
 * 「存在且启用却越界」的元素报 {@code orgScope}（见 {@code FormPayloadValidator#validateOrgScope}），
 * 一个元素不会同时拿到两条互相重叠的错误。
 */
@Component
public class FormOrgScopeChecker implements OrgScopeChecker {

    private final ApproverDirectory directory;

    public FormOrgScopeChecker(ApproverDirectory directory) {
        this.directory = directory;
    }

    @Override
    public boolean withinInitiatorCompanySubtree(String orgId, Long companyId) {
        Long nodeId = parseId(orgId);
        if (nodeId == null || companyId == null) {
            return false;
        }
        Optional<OrgNodeView> node = directory.org(nodeId);
        if (node.isEmpty() || !node.get().active()) {
            return false;
        }
        Optional<OrgNodeView> company = directory.org(companyId);
        if (company.isEmpty() || !company.get().active()) {
            return false;
        }
        return OrgHierarchy.isDescendantOrSelf(node.get().path(), company.get().path());
    }

    /** 只接受十进制 id（与 {@code FormPickerDirectoryChecker#parseId} 同一口径）。 */
    private static Long parseId(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty() || text.length() > 19 || !text.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
