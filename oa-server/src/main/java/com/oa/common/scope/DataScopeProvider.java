package com.oa.common.scope;

/**
 * 数据域解析端口（依赖倒置）。
 *
 * <p>{@code common} 层（拦截器）只依赖本接口，具体实现落在
 * {@code com.oa.authz.app.DataScopeResolver}（从 {@code sys_user} + {@code sys_role} +
 * {@code sys_user_role} + {@code sys_user_position} 装载），避免 common → authz 的反向耦合。
 */
public interface DataScopeProvider {

    /**
     * 装载指定用户的数据域上下文。
     *
     * @param userId 用户 id
     * @return 上下文（永不为 {@code null}；用户不存在时抛 401）
     */
    DataScopeContext resolve(Long userId);
}
