package com.oa.platform.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.common.config.OaProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 不可篡改触发器脚本的解析回归测试（AC-20）。
 *
 * <p><b>守的是哪个坑</b>：{@code ImmutableTriggerInitializer} 早期用非锚定的
 * {@code content.split("//")} 切分脚本，而脚本头注释里出现了**字面量双斜杠**，
 * 于是切分点落在注释内部、产出伪语句，把 {@code CREATE TRIGGER trg_sys_log_no_update}
 * 一起吞掉 → 该触发器永不创建 → **sys_log 的改保护在数据库层完全不存在**（启动日志只有一行 ERROR）。
 *
 * <p>因此本测试断言两件事：① 生产脚本恰好解析出 4 条语句（四张保护一个都不能少）；
 * ② 注释里含字面量双斜杠时也不会误切（用 {@code immutable-triggers-with-comment-slashes.sql} 夹具锁死）。
 */
class ImmutableTriggerScriptTest {

    /** loadStatements 只依赖 ResourceLoader 与 location，故可传 null DataSource。 */
    private final ImmutableTriggerInitializer initializer =
            new ImmutableTriggerInitializer(null, new OaProperties());

    @Test
    @DisplayName("生产脚本解析出 4 条触发器语句，且四条保护（sys_log / flow_signature 的改与删）齐全")
    void productionScriptYieldsAllFourTriggers() throws Exception {
        List<String> statements = initializer.loadStatements("classpath:db/trigger/immutable-triggers.sql");

        assertThat(statements).hasSize(4);
        String all = String.join("\n", statements);
        assertThat(all)
                .contains("trg_sys_log_no_update")
                .contains("trg_sys_log_no_delete")
                .contains("trg_flow_signature_no_update")
                .contains("trg_flow_signature_no_delete");
        assertThat(statements).allSatisfy(sql -> assertThat(sql.toUpperCase()).contains("CREATE TRIGGER"));
    }

    @Test
    @DisplayName("注释里出现字面量双斜杠时不会误切（历史上因此吞掉过 sys_log 的改保护）")
    void literalDoubleSlashInsideCommentDoesNotSwallowTrigger() throws Exception {
        List<String> statements = initializer.loadStatements(
                "classpath:db/trigger/immutable-triggers-with-comment-slashes.sql");

        assertThat(statements).hasSize(2);
        assertThat(statements.get(0)).contains("trg_sys_log_no_update");
        assertThat(statements.get(1)).contains("trg_sys_log_no_delete");
    }

    @Test
    @DisplayName("默认定位属性指向生产脚本（配置漂移会在此暴露）")
    void defaultLocationPointsToProductionScript() {
        assertThat(new OaProperties().getDb().getImmutableTriggersLocation())
                .isEqualTo("classpath:db/trigger/immutable-triggers.sql");
    }
}
