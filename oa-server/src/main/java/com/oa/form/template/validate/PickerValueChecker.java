package com.oa.form.template.validate;

/**
 * {@code user} / {@code org}「人员 / 组织选择」字段的**元素存在性端口**。
 *
 * <h2>真源</h2>
 * <ul>
 *   <li>{@code doc/forms.md} §1.1 类型表：{@code user} / {@code org} 是「人员/组织选择」控件；</li>
 *   <li>{@code doc/forms.md} §2 事项单字段表 {@code cc_users} 行：类型 {@code user}、
 *       校验「**通讯录内**、去重」、长度「≤20 人」；</li>
 *   <li>{@code doc/forms.md} §2 {@code cost_bearer} 行：类型 {@code org}、
 *       校验「限本公司及以下节点」（**范围**限制，由 {@link OrgScopeChecker} 承担
 *       —— {@code rules[orgScope] = initiator_company_subtree}）。</li>
 * </ul>
 *
 * <h2>为什么是端口</h2>
 * <p>与 {@link UniqueValueChecker} 同一理由：{@link FormPayloadValidator} 保持**纯函数**可穷举单测
 * （单测传一个 lambda 即可），生产实现在 {@code com.oa.form.infra.FormPickerDirectoryChecker}，
 * 复用既有的身份目录端口 {@code com.oa.workflow.approver.app.ApproverDirectory}（不另造一份目录查询）。
 *
 * <p>未装配（{@code null}）时**跳过并记 WARN**，与 {@code unique} 规则的缺省口径一致：
 * 「跳过」这件事必须由装配处显式决定，而不是静默变成「永远通过」。
 *
 * <h2>取值范围</h2>
 * <p>只判「该 id 在通讯录/组织树内且**在职/启用**」（存在性），<b>不</b>按调用人的数据域裁剪 ——
 * 存在性是全局事实，按调用人数据域判会让「域外有效组织」被判成「不存在」，
 * 用户看到的是无法自查的错误（同 {@code FormUniqueChecker} 的已定稿理由）。
 */
public interface PickerValueChecker {

    /**
     * @param userId {@code sys_user.id} 的十进制文本（前端选择器的取值形态）
     * @return {@code true} 表示该用户在通讯录内且在职
     */
    boolean userExists(String userId);

    /**
     * @param orgId {@code sys_org.id} 的十进制文本
     * @return {@code true} 表示该组织节点存在且启用
     */
    boolean orgExists(String orgId);
}
