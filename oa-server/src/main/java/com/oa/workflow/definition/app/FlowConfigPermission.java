package com.oa.workflow.definition.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 流程配置面的**权限闸门** —— <b>纯函数</b>（输入「是否系统管理员 + 有效权限码」，输出放行或 403）。
 *
 * <h2>权限码来源</h2>
 * <p>取自 doc 生成物 {@code oa-deploy/sql/04-permissions.sql}（种子 94 权限）：
 * <ul>
 *   <li>{@code admin:flow:template} —— 流程模板（模板列表/详情/节点/版本历史）；</li>
 *   <li>{@code admin:flow:node} —— 节点配置（节点增删改、决议/策略/跳过条件/解析规则）；</li>
 *   <li>{@code admin:flow:publish} —— 发布与停用（开新版本、发布、归档、发布前校验）；</li>
 *   <li>{@code flow} —— 审批动作基础包（发起前预检 / 建实例 / 提交 / 读快照）；</li>
 *   <li>{@code admin:flow} —— 流程管理整支（{@code company_admin} 持有，作发起侧的兜底）。</li>
 * </ul>
 *
 * <p>系统管理员（{@code admin} 角色）一律放行 —— 与 {@code /auth/me} 的 {@code isSuperAdmin}
 * 同源（{@code EffectivePermissionService#isSuperAdmin}）。**有效权限码由调用方注入**
 * （生产由 {@code WorkflowPermissionService} 从 {@code EffectivePermissionService} 取，
 * 单测直接喂集合），因此本类可在无 Spring 容器下被穷举单测。
 *
 * <p><b>不判数据域</b>：模板/节点是**配置数据**（{@code flow_template} / {@code flow_node} 未登记为
 * 数据域受控表，见 {@code application.yml} 的 {@code oa.scope.tables}），访问控制由本权限闸门承担；
 * 单据数据（{@code flow_instance} / {@code flow_task}）仍走数据域织入，二者不混用。
 */
public final class FlowConfigPermission {

    /** 模板读（列表 / 详情 / 节点 / 版本历史 / 校验报告回看）。 */
    public static final String TEMPLATE_READ = "admin:flow:template";

    /** 节点与行为配置写（增删改节点、决议、策略、跳过条件、解析规则）。 */
    public static final String NODE_WRITE = "admin:flow:node";

    /** 版本发布（开新版本 / 发布 / 归档 / 发布前校验）。 */
    public static final String PUBLISH = "admin:flow:publish";

    /** 审批动作基础包（发起前预检 / 建实例 / 提交 / 读快照）。 */
    public static final String FLOW_USE = "flow";

    /** 流程管理整支（发起侧的兜底：{@code company_admin} 持有 {@code admin:flow:*} 但不含 {@code flow}）。 */
    public static final String FLOW_ADMIN = "admin:flow";

    private FlowConfigPermission() {
    }

    /** 是否命中任一权限码（系统管理员恒真；{@code required} 为空表示不设限）。 */
    public static boolean has(Boolean superAdmin, Collection<String> codes, String... required) {
        if (Boolean.TRUE.equals(superAdmin)) {
            return true;
        }
        if (required == null || required.length == 0) {
            return true;
        }
        Set<String> granted = codes == null ? Set.of() : new LinkedHashSet<>(codes);
        for (String code : required) {
            if (granted.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /** 命中任一权限码则返回，否则 403。 */
    public static void require(Boolean superAdmin, Collection<String> codes, String action, String... required) {
        if (!has(superAdmin, codes, required)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "无权执行「" + action + "」：需要权限 " + String.join(" 或 ", required))
                    .withDetail("requiredPermissions", List.of(required));
        }
    }

    /** 发起侧权限（预检 / 建实例 / 提交 / 读快照）：{@code flow} 或 {@code admin:flow} 均可。 */
    public static void requireInitiator(Boolean superAdmin, Collection<String> codes, String action) {
        require(superAdmin, codes, action, FLOW_USE, FLOW_ADMIN);
    }
}
