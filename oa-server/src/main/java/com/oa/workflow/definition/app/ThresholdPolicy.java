package com.oa.workflow.definition.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 节点通过阈值（{@code flow_node.pass_threshold}）的解析、校验与判定 —— <b>纯函数</b>。
 *
 * <h2>权威口径（逐条可追溯）</h2>
 * <ul>
 *   <li>doc/templates.md §1.0「阈值规则」：支持百分比（如 {@code 66%}）与绝对人数（如 {@code 2}）；
 *       <b>绝对人数优先</b>；{@code NULL} 视为过半；百分比<b>向上取整</b>。</li>
 *   <li>doc/templates.md §0 T-07：{@code pass_threshold = NULL} 视为过半；百分比向上取整；
 *       百分比与绝对人数同时存在时<b>绝对人数优先</b>。</li>
 *   <li>doc/prd-0.1.md §5.4「配置约束」：通过阈值支持「百分比」与「绝对人数」两种写法，
 *       二者同时存在时以绝对人数优先。</li>
 *   <li>doc/data-model.md §4.2 {@code pass_threshold}：{@code "50%"} 或 {@code "2"}；
 *       {@code NULL} = 按「过半」（需通过人数 = 向下取整(候选人数/2)+1）。</li>
 * </ul>
 *
 * <h2>「同时存在」在单列 VARCHAR 下怎么表达</h2>
 * <p>{@code pass_threshold} 是**单列**，因此「同时存在」只可能来自**写入请求**同时给出了
 * 绝对人数与百分比两个字段（见 {@code FlowDefinitionDtos.NodeDecisionRequest}）。
 * {@link #compose(Integer, Integer)} 是这一口径的唯一落点：两者都给 → <b>写绝对人数</b>，
 * 并在 {@link Composed#absoluteWins()} 上留痕，供接口回显「本次以绝对人数为准」。
 *
 * <h2>取值合法性</h2>
 * <ul>
 *   <li>绝对人数：整数 {@code 1..99}（{@code 0} 或负数无意义，直接拒绝）；</li>
 *   <li>百分比：整数 {@code 1%..100%}（{@code 0%} 表示「无人通过也算通过」，属配置错误）。</li>
 * </ul>
 */
public final class ThresholdPolicy {

    /** {@code "2"} / {@code "66%"} 两种写法。 */
    private static final Pattern ABSOLUTE = Pattern.compile("^[0-9]{1,3}$");
    private static final Pattern PERCENT = Pattern.compile("^([0-9]{1,3})%$");

    /** 绝对人数允许的上界（与 {@code FlowGateEnums.MAX_COUNT_LIMIT} 同口径防空转）。 */
    public static final int MAX_ABSOLUTE = 99;

    private ThresholdPolicy() {
    }

    /** 阈值的解析结果（不可变）。 */
    public record Threshold(
            DecisionMode mode,
            /** 绝对人数（未配置为 {@code null}）。 */
            Integer absolute,
            /** 百分比（未配置为 {@code null}）。 */
            Integer percent,
            /** 实际需要的同意人数；{@code mode=ALL} 时才有意义。 */
            int requiredApprovals,
            /** 判定依据（{@code absolute} / {@code percent} / {@code majority}）。 */
            String basis,
            /** 候选人数是否足以达到阈值（{@code false} 表示该节点在当前候选人下**永远无法通过**）。 */
            boolean satisfiable
    ) {
    }

    /** 写入侧的合成结果。 */
    public record Composed(String value, boolean absoluteWins) {
    }

    // ================================================================ 解析

    /**
     * 解析阈值的字面量。
     *
     * @return 绝对人数 / 百分比的二元组；{@code null} 输入返回「两者皆空」
     */
    public static Parsed parse(String value) {
        if (value == null || value.isBlank()) {
            return new Parsed(null, null);
        }
        String text = value.trim();
        Matcher percent = PERCENT.matcher(text);
        if (percent.matches()) {
            int number = Integer.parseInt(percent.group(1));
            if (number < 1 || number > 100) {
                throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                        "会签百分比阈值必须在 1%~100% 之间，实际「" + text + "」");
            }
            return new Parsed(null, number);
        }
        if (ABSOLUTE.matcher(text).matches()) {
            int number = Integer.parseInt(text);
            if (number < 1 || number > MAX_ABSOLUTE) {
                throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                        "会签绝对人数阈值必须在 1~" + MAX_ABSOLUTE + " 之间，实际「" + text + "」");
            }
            return new Parsed(number, null);
        }
        throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                "会签阈值只支持「绝对人数」（如 \"2\"）或「百分比」（如 \"66%\"）两种写法，实际「" + text + "」");
    }

    /** 解析结果的原始形态（未做「优先」裁决）。 */
    public record Parsed(Integer absolute, Integer percent) {

        public boolean isEmpty() {
            return absolute == null && percent == null;
        }
    }

    /**
     * 写入侧合成：**绝对人数优先**（templates.md T-07 / prd §5.4）。
     *
     * @param absolute 绝对人数（可为 {@code null}）
     * @param percent  百分比（可为 {@code null}）
     * @return 落库字面量与「是否发生了绝对优先」标记；两者皆空返回 {@code value=null}
     */
    public static Composed compose(Integer absolute, Integer percent) {
        if (absolute != null) {
            if (absolute < 1 || absolute > MAX_ABSOLUTE) {
                throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                        "会签绝对人数阈值必须在 1~" + MAX_ABSOLUTE + " 之间，实际 " + absolute);
            }
            return new Composed(String.valueOf(absolute), percent != null);
        }
        if (percent != null) {
            if (percent < 1 || percent > 100) {
                throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                        "会签百分比阈值必须在 1%~100% 之间，实际 " + percent);
            }
            return new Composed(percent + "%", false);
        }
        return new Composed(null, false);
    }

    // ================================================================ 判定

    /**
     * 按候选人集合解析通过条件（{@code POST /flow-nodes/{id}/decision/resolve} 的唯一口径）。
     *
     * @param mode           决议模式（{@code any} / {@code all} / {@code sequence}）
     * @param thresholdValue {@code pass_threshold} 字面量（可为 {@code null} = 过半）
     * @param candidateCount 候选人数量
     */
    public static Threshold resolve(DecisionMode mode, String thresholdValue, int candidateCount) {
        int candidates = Math.max(candidateCount, 0);
        DecisionMode effective = mode == null ? DecisionMode.ANY : mode;
        Parsed parsed = parse(thresholdValue);
        int required;
        String basis;
        if (effective == DecisionMode.ANY) {
            // 或签：任一人通过即节点通过（PRD §5.4），阈值不参与判定
            required = candidates == 0 ? 0 : 1;
            basis = "any";
        } else if (parsed.absolute() != null) {
            required = parsed.absolute();
            basis = "absolute";
        } else if (parsed.percent() != null) {
            // 百分比**向上取整**（templates.md §1.0 / T-07）
            required = (int) Math.ceil(candidates * parsed.percent() / 100.0d);
            basis = "percent";
        } else {
            // NULL = 过半：向下取整(候选人数/2)+1（doc/data-model.md §4.2）
            required = candidates == 0 ? 0 : candidates / 2 + 1;
            basis = "majority";
        }
        boolean satisfiable = candidates == 0 || required <= candidates;
        return new Threshold(effective, parsed.absolute(), parsed.percent(), required, basis, satisfiable);
    }

    // ================================================================ 校验

    /**
     * 节点级阈值校验（供发布前 dry-run 报告聚合；**不抛异常**）。
     *
     * @param mode           决议模式（{@code null} = 不适用，仅⑦归档登记节点）
     * @param thresholdValue 阈值的字面量
     * @return 问题清单（空 = 合法）
     */
    public static List<String> violations(String nodeLabel, DecisionMode mode, String thresholdValue) {
        List<String> problems = new ArrayList<>();
        String prefix = nodeLabel == null || nodeLabel.isBlank() ? "" : nodeLabel + "：";
        if (thresholdValue != null && !thresholdValue.isBlank()) {
            try {
                parse(thresholdValue);
            } catch (BizException ex) {
                problems.add(prefix + ex.getMessage());
            }
            if (mode == null) {
                problems.add(prefix + "归档登记节点（decision_mode 为空）不适用通过阈值，请清空 pass_threshold");
            } else if (mode != DecisionMode.ALL) {
                problems.add(prefix + "pass_threshold 仅在「会签（all）」下参与判定，当前决议模式为 "
                        + mode.code() + "，该阈值不会生效");
            }
        }
        if (mode == DecisionMode.ALL && (thresholdValue == null || thresholdValue.isBlank())) {
            // 合法：缺省即「过半」（T-07）。此处刻意不报错，仅由文档与前端提示。
            return problems;
        }
        return problems;
    }

    /** 阈值列是否为空（{@code null} / 空串 / {@code "null"} 之外的空写法）。 */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value.trim().toLowerCase(Locale.ROOT));
    }
}
