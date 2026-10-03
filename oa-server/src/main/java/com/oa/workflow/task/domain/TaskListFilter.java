package com.oa.workflow.task.domain;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.runtime.domain.RuntimeEnums.InstanceStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.SubStatus;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 审批中心列表的**统一筛选条件**（待我审批 / 我已审批 / 我发起的 / 抄送我的 **同一套参数**）。
 *
 * <h2>参数清单（四个端点完全一致，命名一致）</h2>
 * <table border="1">
 *   <tr><th>参数</th><th>含义</th><th>SQL 落点</th></tr>
 *   <tr><td>{@code keyword}</td><td>关键字：**单号 / 标题 / 发起人**</td>
 *       <td>{@code i.biz_no}、{@code form_data.fields_json.title}、{@code sys_user.name}</td></tr>
 *   <tr><td>{@code formType}</td><td>单据类型：{@code matter / fund / contract / seal}</td>
 *       <td>{@code i.form_type}</td></tr>
 *   <tr><td>{@code status}</td><td>单据状态：{@code draft/approving/approved/rejected/withdrawn/terminated}
 *       ，另接受子状态 {@code pending_supplement}（待补件）</td>
 *       <td>{@code i.status} / {@code i.sub_status}</td></tr>
 *   <tr><td>{@code dateFrom} / {@code dateTo}</td><td>起止日期（{@code YYYY-MM-DD}，含首含尾）</td>
 *       <td><b>按列表各自的时间轴</b>：待我审批 / 我发起的 / 抄送我的 = {@code i.created_at}
 *       （**发起时间**）；我已审批 = {@code t.decided_at}（**我处理该任务的时间**，2026-10-05 裁定）</td></tr>
 * </table>
 *
 * <h2>为什么做成值对象而不是散装 {@code @RequestParam}</h2>
 * <ol>
 *   <li><b>同一套参数</b>：四个端点共用本类，命名与口径不可能各自漂移；</li>
 *   <li><b>空值等价于不筛</b>：{@link #of} 把空白串统一归一成 {@code null}，
 *       调用方（与 SQL 的 {@code <if>}）只需判 {@code hasXxx()}；</li>
 *   <li><b>SQL 层过滤</b>：本类只做**归一化与合法性校验**，不过滤行数据；
 *       真正的过滤在 {@code FlowTaskMapper.xml} 的 WHERE 里（LIMIT/OFFSET 之前），
 *       因此分页 {@code total} 是「筛选后的总数」，不是「当前页里再筛」。</li>
 * </ol>
 *
 * <h2>取值非法的处理</h2>
 * <p>非法 {@code formType} / {@code status} / 日期一律抛 400 {@link ErrorCode#PARAM_INVALID}
 * 并给出**合法取值清单**（不静默忽略：静默忽略会让用户以为筛选生效、实际看到的是全量）。
 *
 * <p><b>日期口径（2026-10-05 裁定，替换此前「四个端点同义」的临时口径）</b>：
 * 「我已审批」按 <b>{@code flow_task.decided_at}</b>（我处理该任务的时间）筛 ——
 * 该列表回答的是「我哪天办的那张单」，与「我发起的」按发起时间筛是**两条不同的时间轴**，
 * 同名参数因此必须由出参如实披露（{@code PageResult.dateField}：{@code decidedAt} /
 * {@code createdAt}），否则用户「筛了却看不出按什么筛」。待我审批 / 我发起的 / 抄送我的
 * 仍按 {@code flow_instance.created_at}（发起时间）。日期条件由
 * {@code FlowTaskMapper.xml} 的 {@code TaskListFilters} 片段按调用方注入的日期列拼装
 * （列名是 mapper 内部常量，不来自请求参数）。
 */
public record TaskListFilter(String keyword, String formType, String status, String subStatus,
                             String dateFrom, String dateTo) {

    /** 四类单据（{@code flow_instance.form_type}，doc/forms.md §2–§5）。 */
    public static final Set<String> FORM_TYPES = new LinkedHashSet<>(Set.of("matter", "fund", "contract", "seal"));

    /** LIKE 的转义字符（避开反斜杠，见本类 {@link #escapeLike}）。 */
    public static final char LIKE_ESCAPE = '!';

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** 不筛（四个端点的缺省）。 */
    public static TaskListFilter none() {
        return new TaskListFilter(null, null, null, null, null, null);
    }

    /**
     * 归一化 + 校验（空白 → {@code null}；非法取值 → 400）。
     *
     * @param status 单据状态码；子状态 {@code pending_supplement} 也接受（落到 {@code sub_status}）
     */
    public static TaskListFilter of(String keyword, String formType, String status, String dateFrom, String dateTo) {
        String normalizedFormType = null;
        if (notBlank(formType)) {
            String code = formType.trim().toLowerCase(Locale.ROOT);
            if (!FORM_TYPES.contains(code)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "单据类型取值非法：" + formType.trim() + "（合法取值：" + String.join(" / ", FORM_TYPES) + "）");
            }
            normalizedFormType = code;
        }

        String normalizedStatus = null;
        String normalizedSubStatus = null;
        if (notBlank(status)) {
            String code = status.trim().toLowerCase(Locale.ROOT);
            if (InstanceStatus.of(code).isPresent()) {
                normalizedStatus = code;
            } else if (SubStatus.of(code).isPresent()) {
                normalizedSubStatus = code;
            } else {
                Set<String> allowed = new LinkedHashSet<>();
                for (InstanceStatus value : InstanceStatus.values()) {
                    allowed.add(value.code());
                }
                for (SubStatus value : SubStatus.values()) {
                    allowed.add(value.code());
                }
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "单据状态取值非法：" + status.trim() + "（合法取值：" + String.join(" / ", allowed) + "）");
            }
        }

        String from = date(dateFrom, "dateFrom");
        String to = date(dateTo, "dateTo");
        if (from != null && to != null && from.compareTo(to) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "起止日期不合法：dateFrom(" + from + ") 不能晚于 dateTo(" + to + ")");
        }
        return new TaskListFilter(notBlank(keyword) ? keyword.trim() : null,
                normalizedFormType, normalizedStatus, normalizedSubStatus, from, to);
    }

    public boolean hasKeyword() {
        return keyword != null;
    }

    public boolean hasFormType() {
        return formType != null;
    }

    public boolean hasStatus() {
        return status != null;
    }

    public boolean hasSubStatus() {
        return subStatus != null;
    }

    public boolean hasDateFrom() {
        return dateFrom != null;
    }

    public boolean hasDateTo() {
        return dateTo != null;
    }

    /** 是否完全没筛（出参取证与日志用）。 */
    public boolean isEmpty() {
        return !hasKeyword() && !hasFormType() && !hasStatus() && !hasSubStatus()
                && !hasDateFrom() && !hasDateTo();
    }

    /** 关键字 → {@code LIKE} 模式（两侧通配 + 转义 {@code % _ !}），配合 {@code ESCAPE '!'}。 */
    public String keywordLike() {
        if (keyword == null) {
            return null;
        }
        return "%" + escapeLike(keyword) + "%";
    }

    /**
     * 转义 LIKE 的通配符。
     *
     * <p>为什么必须转义：用户输入 {@code %} 时，未转义的 {@code LIKE '%%%'} 会退化成「匹配全部」，
     * 看起来「筛选没生效」；{@code _} 更隐蔽（匹配任意单字符）。转义字符取 {@code !}
     * 而非反斜杠：MySQL 字符串字面量里反斜杠本身还要再转义一次（{@code '\\'}），
     * 容易在 XML 与 JDBC 两处各错一次。
     */
    private static String escapeLike(String raw) {
        StringBuilder builder = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == LIKE_ESCAPE || ch == '%' || ch == '_') {
                builder.append(LIKE_ESCAPE);
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    private static String date(String raw, String name) {
        if (!notBlank(raw)) {
            return null;
        }
        String text = raw.trim();
        if (!DATE.matcher(text).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    name + " 日期格式不合法：" + text + "（应为 YYYY-MM-DD）");
        }
        try {
            LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, name + " 不是合法日期：" + text);
        }
        return text;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
