package com.oa.common.scope;

/**
 * 被数据域过滤保护的表类型 —— 决定织入哪一种 WHERE 口径。
 *
 * <p>取值与 {@link DataScopeSqlBuilder} 的两个入口一一对应。
 */
public enum DataScopeKind {

    /** 流程实例类表（{@code flow_instance} 及其从表）：按单据五口径过滤。 */
    INSTANCE,

    /** 用户/通讯录表（{@code sys_user}）：按本人、本部门子树、本公司过滤。 */
    USER,

    /** 不参与数据域过滤（仅白名单登记用）。 */
    NONE
}
