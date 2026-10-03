package com.oa.form.infra;

import com.oa.form.template.validate.PickerValueChecker;
import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.Candidate;
import com.oa.workflow.approver.app.OrgNodeView;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@link PickerValueChecker} 的生产实现（{@code user} / {@code org} 选择器的元素存在性）。
 *
 * <h2>复用既有目录端口，不另造一份查询</h2>
 * <p>人员与组织都走 {@link ApproverDirectory}（{@code com.oa.workflow.approver.infra.JdbcApproverDirectory}
 * + {@code ApproverDirectoryMapper} 的带 {@code @dataScope} 标记语句）。理由：
 * <ul>
 *   <li>「通讯录里有没有这个人 / 组织树里有没有这个节点」在系统里**已经有唯一查询口径**，
 *       再写一套 SQL 只会多一处会漂移的真相；</li>
 *   <li>该端口在**系统口径**下执行（{@code DataScopeContext.system()}）：存在性是全局事实，
 *       与调用人的数据域无关 —— 按调用人数据域判会把域外有效 id 报成「不存在」，
 *       用户拿到的是无法自查的错误（同 {@code FormUniqueChecker} 的已定稿理由）。</li>
 * </ul>
 *
 * <h2>只回答存在性，不返回内容</h2>
 * <p>本实现只返回布尔（不含姓名/组织名），因此不构成数据域读取旁路：
 * 校验器只把「第 N 项不是有效人员」写进错误项，不回显通讯录内容。
 *
 * <h2>有效性的口径</h2>
 * <ul>
 *   <li>用户：存在、未逻辑删除，且 {@link Candidate#assignable()}（{@code status='active'}，
 *       即在职；离职/停用不算「通讯录内」）；</li>
 *   <li>组织：存在、未逻辑删除，且状态非 {@code disabled}。</li>
 * </ul>
 */
@Component
public class FormPickerDirectoryChecker implements PickerValueChecker {

    private final ApproverDirectory directory;

    public FormPickerDirectoryChecker(ApproverDirectory directory) {
        this.directory = directory;
    }

    @Override
    public boolean userExists(String userId) {
        Long id = parseId(userId);
        if (id == null) {
            return false;
        }
        Optional<Candidate> candidate = directory.user(id);
        return candidate.isPresent() && candidate.get().assignable();
    }

    @Override
    public boolean orgExists(String orgId) {
        Long id = parseId(orgId);
        if (id == null) {
            return false;
        }
        return directory.org(id).map(FormPickerDirectoryChecker::enabled).orElse(false);
    }

    private static boolean enabled(OrgNodeView node) {
        String status = node.status();
        return status == null || "active".equals(status.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 只接受十进制 id（前端选择器的取值形态是 {@code option.value} = id 字符串）。
     *
     * <p>非十进制取值（如模板里的符号型占位 {@code initiator_company}、或被人手填的姓名）
     * 一律判为**无效元素**：{@code doc/templates.md} §2.2 明确符号型占位是「界面预填指令」、
     * **不是可落库取值**，所以它落到 {@code fields_json} 里本就该被拒。
     */
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
