package com.oa.common.scope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注实体对应的数据库表参与数据域过滤（唯一入口织入的登记点）。
 *
 * <p>用法：贴在 {@code domain} 层实体上，例如
 * <pre>{@code
 * @TableName("sys_user")
 * @DataScopeTable(table = "sys_user", alias = "u", kind = DataScopeKind.USER)
 * public class SysUser { ... }
 * }</pre>
 *
 * <p>织入点：Mapper XML 中在 WHERE 处写标记注释
 * {@code /* @dataScope(table=sys_user, alias=u) *}{@code /}，
 * 由 {@link DataScopeInterceptor} 在运行时替换为 {@link DataScopeSqlBuilder} 产出的纯函数片段。
 * **标注了本注解的表，在已认证上下文中出现未带标记的 SELECT 会被直接拦截**
 * （对应 doc/tech-design.md §5.3「禁止在业务代码里手写绕过过滤的裸查询」评审阻断项）。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScopeTable {

    /** 表名（数据库真实表名，小写）。 */
    String table();

    /** Mapper SQL 中该表使用的别名（默认 {@code i}，与 data-model.md §7.2 伪 SQL 一致）。 */
    String alias() default "i";

    /** 过滤口径。 */
    DataScopeKind kind() default DataScopeKind.INSTANCE;
}
