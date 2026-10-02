package com.oa.identity.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.identity.app.InFlightChecker.InFlightSummary;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在途/待办拦截判定 —— <b>纯函数</b>（输入 {@link InFlightSummary} + 开关，输出结论或抛异常）。
 *
 * <p>依据：
 * <ul>
 *   <li>doc/prd-0.1.md §5.5：「组织节点停用——停用前必须处理完该节点全部在途单据；
 *       员工离职——离职前必须处理完名下全部待办；系统提示未处理任务数量，强制要求先转办或改派」；</li>
 *   <li>doc/test-cases.md AC-12 / TC-ORG-004：操作被阻止，提示未处理待办数量＝2，状态保持不变；</li>
 *   <li>doc/import-spec.md §8.1/§8.2 的**提示文案**与 §7.2 的清单字段（数量 + 单号）。</li>
 * </ul>
 *
 * <p>开关：{@code oa.identity.block-on-inflight}（默认 {@code true}）。
 * {@code true} → 命中即 409 拒绝并把影响清单写进对外文案；{@code false} → 仅告警放行，
 * 但**仍把影响清单返回给调用方**（前端据此二次确认）。
 *
 * <h2>强制继续（force）的放行语义 —— 业务裁定</h2>
 * AC-11 / AC-12 的默认阻断**依然生效**，只被「系统管理员显式声明的强制继续」覆盖：
 * <ul>
 *   <li><b>触发条件</b>：{@code force=true} 只能由系统管理员 + **必填原因**触发；
 *       该准入校验在**控制器**由 {@link ForceReasonPolicy#assertAllowed} 把关
 *       （{@code reason} 为空 → 400；非 {@code admin} → 403），本类**不做**角色判断，
 *       只按调用方传入的 {@code force} 决定「抛异常」还是「返回结论」——避免两处口径分叉；</li>
 *   <li><b>双留痕</b>：覆盖发生时必须留两道痕迹 ——
 *       ①{@code reason}/{@code force} 由 {@code AuditAspect} 序列化进 {@code sys_log}（审计切面）；
 *       ②运行日志由 {@link #logForceOverride} 输出（subject / 对象名 / 在途数 / 待办数 / 经办人 / reason）；</li>
 *   <li><b>覆盖只影响「是否抛异常」</b>：{@link Outcome#rejected()} 仍为 {@code true}
 *       （即「按开关本应拒绝」这一事实不变），调用方据此写放行日志与出参文案。</li>
 * </ul>
 */
public final class InFlightGuard {

    private static final Logger log = LoggerFactory.getLogger(InFlightGuard.class);

    /** 拦截文案中的主语类型（决定文案措辞，避免在 Service 里散落字符串）。 */
    public enum Subject {

        /** 员工离职。 */
        USER_RESIGN("离职前必须清空名下待办", "条未处理待办", "请先转办或改派"),
        /**
         * 人员停用（后台单条操作，import-spec T-05）。
         *
         * <p>与离职**同一阻断口径**：停用后该账号不可登录，名下待办同样会无人处理，
         * 因此 {@code PUT /users/{id}} 切到 {@code disabled} 时复用本判定。
         */
        USER_DISABLE("停用前必须清空名下待办", "条未处理待办", "请先转办或改派"),
        /** 组织（含子树）停用。 */
        ORG_DISABLE("组织停用前必须清空在途单据", "张在途单据", "请先办结或流转处理");

        private final String prefix;
        private final String unit;
        private final String advice;

        Subject(String prefix, String unit, String advice) {
            this.prefix = prefix;
            this.unit = unit;
            this.advice = advice;
        }

        public String prefix() {
            return prefix;
        }
    }

    /** 判定结论（不可变，可直接作为接口出参的一部分）。 */
    public record Outcome(
            Subject subject,
            String subjectName,
            boolean blockOnInflight,
            InFlightSummary summary,
            boolean blocked,
            boolean rejected,
            String message
    ) {
    }

    /** 单号在文案中最多列出几条（避免提示文案无限长）。 */
    private static final int MAX_BIZ_NO_IN_MESSAGE = 10;

    private InFlightGuard() {
    }

    /**
     * 评估影响（**不抛异常**）：{@code GET /orgs/{id}/in-flight-check} 与
     * {@code POST .../resign|disable} 的「仅告警」分支都用它。
     */
    public static Outcome evaluate(Subject subject, String subjectName, InFlightSummary summary, boolean blockOnInflight) {
        InFlightSummary effective = summary == null ? InFlightSummary.none() : summary;
        boolean blocked = effective.hasInFlight();
        boolean rejected = blocked && blockOnInflight;
        String message = blocked ? buildMessage(subject, subjectName, effective) : "无在途单据与待办，可继续操作";
        return new Outcome(subject, subjectName, blockOnInflight, effective, blocked, rejected, message);
    }

    /**
     * 评估并在「命中 + 开关为拒绝」时抛 409（{@link ErrorCode#CONFLICT}）—— 默认口径（{@code force=false}）。
     *
     * <p>对外文案必须自带**数量 + 原因 + 单号**（施工要求第 3 条「返回影响清单」；
     * {@code BizException} 的 details 不进 HTTP 响应体，故清单必须写进 message）。
     *
     * <p>保留 4 参签名并委托 {@code force=false}，既有调用与测试语义**完全不变**。
     */
    public static Outcome assertClear(Subject subject, String subjectName, InFlightSummary summary, boolean blockOnInflight) {
        return assertClear(subject, subjectName, summary, blockOnInflight, false);
    }

    /**
     * 评估并在「命中 + 开关为拒绝」时抛 409，但**允许系统管理员以 force 放行**。
     *
     * <p>语义：
     * <ul>
     *   <li>{@code force=true} 且 {@link Outcome#rejected()} → <b>不抛异常</b>，直接返回该 {@code outcome}
     *       （其 {@code rejected()} 仍为 {@code true}，调用方据此走运行日志与出参文案）；</li>
     *   <li>其它情况（{@code force=false}，或未命中/开关为「仅告警」）→ 与 4 参版本**逐字一致**。</li>
     * </ul>
     *
     * <p><b>职责边界</b>：{@code force} 的授权（系统管理员 + 必填原因）由控制器侧的
     * {@link ForceReasonPolicy#assertAllowed} 完成，本方法**不做**任何角色判断，也不读取 HTTP 上下文；
     * 调用方在放行分支应调用 {@link #logForceOverride} 补运行日志（审计留痕由 {@code @Audited} 承担）。
     *
     * @param force 是否已获授权的「强制继续」；{@code false} 表示保持 AC-11/AC-12 的默认阻断
     */
    public static Outcome assertClear(Subject subject, String subjectName, InFlightSummary summary,
                                      boolean blockOnInflight, boolean force) {
        Outcome outcome = evaluate(subject, subjectName, summary, blockOnInflight);
        if (outcome.rejected() && !force) {
            throw new BizException(ErrorCode.CONFLICT, outcome.message())
                    .withDetail("subject", subject.name())
                    .withDetail("inFlightInstances", outcome.summary().inFlightInstances())
                    .withDetail("pendingTasks", outcome.summary().pendingTasks())
                    .withDetail("bizNos", outcome.summary().bizNos());
        }
        return outcome;
    }

    /**
     * 本次是否真的发生了「强制继续放行」：命中阻断（{@code blocked && blockOnInflight}）且显式 {@code force=true}。
     *
     * <p>供调用方区分「开关为仅告警而放行」与「被管理员强制覆盖后放行」（两者的出参文案不同）。
     */
    public static boolean forced(Outcome outcome, Boolean force) {
        return outcome != null && outcome.rejected() && Boolean.TRUE.equals(force);
    }

    /**
     * 覆盖发生时输出运行日志（可观测性，需求「覆盖必须双留痕」的第二道痕迹）。
     *
     * <p>只在 {@link #forced} 成立时输出 {@code log.warn}，含
     * <b>subject、对象名、在途数、待办数、涉及单号、经办人、reason</b>，便于运维审计；
     * {@code reason}/{@code force} 另由审计切面落 {@code sys_log}（第一道痕迹），此处不重复写库。
     *
     * <p>调用方（服务层）在 {@code assertClear(..., force)} 之后**无条件**调用本方法即可，内部自行判定。
     */
    public static void logForceOverride(Outcome outcome, Boolean force, String reason) {
        if (!forced(outcome, force)) {
            return;
        }
        InFlightSummary summary = outcome.summary() == null ? InFlightSummary.none() : outcome.summary();
        DataScopeContext context = DataScopeContext.current();
        CurrentUser operator = context == null ? null : context.getPrincipal();
        log.warn("强制继续放行在途/待办阻断（AC-52 双留痕：reason/force 由审计切面落 sys_log）："
                        + "subject={} object={} inFlightInstances={} pendingTasks={} bizNos={} operator={} reason={}",
                outcome.subject(), outcome.subjectName(),
                summary.inFlightInstances(), summary.pendingTasks(), summary.bizNos(),
                operator == null ? "<unknown>" : operator.account() + "(" + operator.id() + ")",
                reason == null || reason.isBlank() ? "<未填写>" : reason);
    }

    /** 按 import-spec §8.1/§8.2 的文案口径拼装提示。 */
    public static String buildMessage(Subject subject, String subjectName, InFlightSummary summary) {
        InFlightSummary effective = summary == null ? InFlightSummary.none() : summary;
        String name = subjectName == null || subjectName.isBlank() ? "该对象" : subjectName;
        StringBuilder builder = new StringBuilder();
        builder.append(subject.prefix).append('：').append(name);
        if (subject == Subject.ORG_DISABLE) {
            // 「集团/公司A 及其子树下仍有 3 张在途单据」
            builder.append(" 及其子树下仍有 ").append(effective.inFlightInstances()).append(' ')
                    .append(subject.unit);
            if (effective.pendingTasks() > 0) {
                builder.append("（另有 ").append(effective.pendingTasks()).append(" 条待处理待办）");
            }
        } else {
            builder.append(" 名下仍有 ").append(effective.pendingTasks()).append(' ')
                    .append(subject.unit);
            if (effective.inFlightInstances() > 0) {
                builder.append("（另涉及 ").append(effective.inFlightInstances()).append(" 张在途单据）");
            }
        }
        builder.append('，').append(subject.advice);
        List<String> bizNos = effective.bizNos();
        if (bizNos != null && !bizNos.isEmpty()) {
            builder.append("；涉及单号：");
            int limit = Math.min(bizNos.size(), MAX_BIZ_NO_IN_MESSAGE);
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    builder.append('、');
                }
                builder.append(bizNos.get(i));
            }
            if (bizNos.size() > limit) {
                builder.append(" 等 ").append(bizNos.size()).append(" 张");
            }
        }
        return builder.toString();
    }
}
