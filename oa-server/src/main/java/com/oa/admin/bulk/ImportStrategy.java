package com.oa.admin.bulk;

/**
 * 单类导入策略（{@code org.csv} / {@code user.csv} / {@code org_leader.csv} /
 * {@code user_position.csv} / {@code user_role.csv}）。
 *
 * <p>两段式（import-spec §5.3）：
 * <ol>
 *   <li>{@link #validate} = 阶段 A 全量校验（**不提前中断**，把所有 error 写进报告）；</li>
 *   <li>{@link #write} = 阶段 C 落库（**只在校验 0 error 时被调用**，由调用方保证单事务）。</li>
 * </ol>
 *
 * <p>返回 {@code Object} 而不做泛型参数化：每类策略的「待落库行」结构不同，
 * 而调用方（{@code BulkImportService}）只做透传 —— 泛型参数在这里只会制造
 * unchecked 转换噪音，策略内部自行强转并保证类型一致。
 */
public interface ImportStrategy {

    ImportKind kind();

    /** 阶段 A：全量校验；发现写入 {@code report}，返回解析后的行集合。 */
    Object validate(CsvTable table, ImportContext context, ImportReport report);

    /** 阶段 C：事务内落库，并把 added/updated/skipped 写回 {@code report}。 */
    void write(Object parsed, ImportContext context, ImportReport report);
}
