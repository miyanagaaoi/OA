package com.oa.workflow.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.oa.workflow.definition.domain.FlowGateEnums.WithdrawWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * <b>微任务 0</b>：{@code flow_template.withdraw_window} 取到非法字符串时**必须留下 WARN（含原始值）**。
 *
 * <h2>守的坑（2026-10-04 微修）</h2>
 * <p>该列原先写作 {@code WithdrawWindow.of(...).orElse(null)}：绕过 DB 的
 * {@code chk_flow_template_gates} 硬改进去的非法值会被**静默**映射成 {@code NULL}，
 * 而 {@code NULL} 的语义是「取默认口径 {@code until_finance_approved}」
 * （doc/templates.md §1.8）。行为上二者都是 fail-open（退化为默认口径，这是可接受的），
 * 但「静默」不可接受：管理员手改过的数据会在无人知情的情况下按另一种口径裁决撤回。
 */
class FlowTemplateWithdrawWindowWarnTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(FlowTemplate.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private static FlowTemplate template(String withdrawWindow) {
        FlowTemplate template = new FlowTemplate();
        template.setId(42L);
        template.setCode("matter");
        template.setVersion(1);
        template.setWithdrawWindow(withdrawWindow);
        return template;
    }

    @Test
    @DisplayName("非法取值：记 WARN（含原始值、模板 id、合法取值清单），行为退化为默认口径")
    void illegalValueLogsWarnAndFallsBackToDefault() {
        FlowGatePolicy policy = template("until_chairman_approved").gatePolicy();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        String message = event.getFormattedMessage();
        assertThat(message)
                .as("WARN 必须含原始值，否则运维无法定位是哪一行数据被手改了")
                .contains("until_chairman_approved")
                .contains("42")
                .contains(WithdrawWindow.defaultWindow().code())
                .contains(WithdrawWindow.UNTIL_FINANCE_STARTED.code());

        assertThat(policy.withdrawWindow()).isNull();
        assertThat(policy.effectiveWithdrawWindow())
                .as("fail-open：退化到默认口径（行为与 NULL 完全一致）")
                .isEqualTo(WithdrawWindow.defaultWindow())
                .isEqualTo(WithdrawWindow.UNTIL_FINANCE_APPROVED);
    }

    @Test
    @DisplayName("空值与 NULL：不记日志（这是「未配置」的正常形态，不是异常）")
    void blankValueIsSilent() {
        assertThat(template(null).gatePolicy().effectiveWithdrawWindow())
                .isEqualTo(WithdrawWindow.UNTIL_FINANCE_APPROVED);
        assertThat(template("   ").gatePolicy().effectiveWithdrawWindow())
                .isEqualTo(WithdrawWindow.UNTIL_FINANCE_APPROVED);
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("合法取值：不记日志且语义不变（两种口径都可配，默认取 REQ-FLOW-009）")
    void legalValuesKeepSemantics() {
        assertThat(template("until_finance_approved").gatePolicy().effectiveWithdrawWindow())
                .isEqualTo(WithdrawWindow.UNTIL_FINANCE_APPROVED);
        assertThat(template("UNTIL_FINANCE_STARTED").gatePolicy().effectiveWithdrawWindow())
                .as("大小写不敏感（与原 WithdrawWindow.of 的实现口径一致）")
                .isEqualTo(WithdrawWindow.UNTIL_FINANCE_STARTED);
        assertThat(appender.list).isEmpty();
    }
}
