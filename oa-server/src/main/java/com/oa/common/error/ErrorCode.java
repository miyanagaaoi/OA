package com.oa.common.error;

import java.util.Arrays;

/**
 * 错误码枚举（含 401 / 403 / 409 语义）。
 *
 * <p>约定：
 * <ul>
 *   <li>{@code 0} 成功；</li>
 *   <li>{@code 4xxxx} 客户端错误，其中 {@code 401xx} = 未认证（会话无效 / 口令错误 / 锁定），
 *       {@code 403xx} = 已认证但无权（数据域、字段级、导出），{@code 409xx} = 状态冲突（乐观锁、唯一键、状态机冲突）；</li>
 *   <li>{@code 5xxxx} 服务端错误，对外一律输出泛化文案，禁止泄露内部细节（AC-41）。</li>
 * </ul>
 *
 * <p>注意：{@link #getMessage()} 是**给终端用户看**的文案，内部细节只进日志。
 */
public enum ErrorCode {

    // ---------- 成功 ----------
    SUCCESS(0, "OK", 200),

    // ---------- 400 参数 ----------
    BAD_REQUEST(40000, "请求参数不合法", 400),
    PARAM_INVALID(40001, "参数校验失败", 400),
    PASSWORD_WEAK(40002, "口令强度不足：至少 8 位且同时包含字母与数字", 400),
    PASSWORD_MISMATCH(40003, "两次输入的口令不一致", 400),
    PASSWORD_SAME_AS_OLD(40004, "新口令不能与原口令相同", 400),
    /**
     * 400：批量导入存在 error 行 → **整批拒绝、错误零落库**（import-spec §5.3 阶段 B）。
     *
     * <p>校验报告（逐行错误码 + 中文说明）随响应体返回，调用方据此逐条修正后重传。
     */
    IMPORT_VALIDATION_FAILED(40005, "批量导入校验未通过，整批已拒绝（错误零落库）", 400),
    /** 400：导入文件本身不可用（缺文件 / 非 UTF-8 BOM / 表头不符，import-spec §4.1）。 */
    IMPORT_FILE_INVALID(40006, "导入文件不合法（编码/表头/行结构，见 import-spec §4.1）", 400),
    /**
     * 400：**发起前拦截**——任一节点解析出的候选人集合为空（REQ-FLOW-012 / AC-11 / AC-19）。
     *
     * <p>文案必须自带「哪个节点、命中哪条规则、缺什么配置」（{@code BizException} 的 details
     * 不进 HTTP 响应体，故清单必须写进 message，口径同 {@code InFlightGuard}）。
     * <b>绝不允许静默跳过该节点</b>：静默跳过会让单据绕过审批，属严重内控缺陷。
     */
    APPROVER_RESOLUTION_BLOCKED(40007, "发起被拒绝：存在节点无有效审批人", 400),
    /** 400：模板/节点配置非法（顺序不连续、阈值非法、超时 <24h、必填节点缺失等）。 */
    FLOW_DEFINITION_INVALID(40008, "流程模板或节点配置非法，已拒绝", 400),

    // ---------- 401 认证 ----------
    /** 401：未登录、或会话 Cookie 缺失/已撤销。 */
    UNAUTHORIZED(40101, "未登录或登录状态已失效，请重新登录", 401),
    /** 401：令牌存在但已过期（软撤销 → 过期）。 */
    SESSION_EXPIRED(40102, "登录状态已过期，请重新登录", 401),
    /** 401：账号或口令错误（不区分「账号不存在」，避免账号枚举）。 */
    BAD_CREDENTIALS(40103, "账号或口令错误", 401),
    /** 401：连续失败 5 次锁定 15 分钟（REQ-NFR-005）。 */
    ACCOUNT_LOCKED(40104, "账号已锁定，请于 %d 分钟后重试", 401),
    /** 401：离职 / 停用账号。 */
    ACCOUNT_DISABLED(40105, "账号已停用或已离职，请联系管理员", 401),
    /** 401：首登强制改密（口令未改前除改密与登出外不可用）。 */
    PASSWORD_CHANGE_REQUIRED(40106, "首次登录必须修改口令", 401),

    // ---------- 403 授权 ----------
    /** 403：已登录但角色/权限不足。 */
    FORBIDDEN(40301, "无权执行该操作", 403),
    /** 403：数据域判定不通过（跨公司 / 跨部门越权，AC-22 越权专项）。 */
    DATA_SCOPE_DENIED(40302, "无权访问该数据（数据域校验未通过）", 403),
    /** 403：检测到未织入数据域过滤的裸查询（评审阻断项，doc/tech-design.md §5.3）。 */
    DATA_SCOPE_MISSING(40303, "检测到未织入数据域过滤的查询，已被拦截", 403),
    /** 403：三态白名单拒绝写入（doc/tech-design.md §5.4）。 */
    FIELD_WRITE_DENIED(40304, "当前状态不允许修改字段：%s", 403),
    /** 403：导出权限（仅系统管理员与财务角色，REQ-AUTH-003）。 */
    EXPORT_DENIED(40305, "仅系统管理员与财务角色可导出", 403),
    /**
     * 403：金额字段对当前角色只读（PRD §5.3「合同金额、资金金额对非财务类角色只读展示」）。
     *
     * <p>与 {@link #FIELD_WRITE_DENIED} 的区别：后者是**状态**不允许写（三态白名单），
     * 前者是**角色**不允许写，与单据状态无关，草稿态同样拒绝。
     */
    AMOUNT_READ_ONLY(40306, "金额字段对非财务类角色只读，本请求已被拒绝", 403),
    /** 403：导出字段级限制（金额列被策略剔除，或该导出目标不允许导出该字段）。 */
    EXPORT_FIELD_DENIED(40307, "导出字段受限：该字段不在当前角色可导出的列内", 403),

    // ---------- 404 / 405 ----------
    NOT_FOUND(40401, "资源不存在", 404),
    METHOD_NOT_ALLOWED(40501, "请求方法不被支持", 405),

    // ---------- 409 冲突 ----------
    /** 409：通用状态冲突（状态机不允许的动作）。 */
    CONFLICT(40901, "当前状态不允许该操作，请刷新后重试", 409),
    /** 409：唯一键冲突（一单一号、会签重复、同名角色等）。 */
    DUPLICATE(40902, "数据已存在（唯一约束冲突）", 409),
    /** 409：并发修改冲突（version 乐观锁）。 */
    CONCURRENT_MODIFIED(40903, "数据已被他人修改，请刷新后重试", 409),
    /** 409：同一账号第 4 台设备登录，最早会话被踢出（REQ-NFR-006）。 */
    SESSION_LIMIT_REACHED(40904, "同时在线设备已达上限，最早的会话已被踢出", 409),
    /** 409：同一时刻只允许一个导入任务（import-spec §6.4 分布式锁 {@code oa:import:org_user}）。 */
    IMPORT_IN_PROGRESS(40905, "已有导入任务进行中，请等待其结束后重试", 409),
    /**
     * 409：模板版本不可写（templates.md §3.3 状态机 / §4.3 禁止事项）。
     *
     * <p>{@code published} / {@code archived} 版本**只读**：改配置必须
     * {@code POST /flow-templates/{id}/versions} 开新草稿版本，
     * 直接改会让在途单据的渲染与审批结果不可复现。
     */
    FLOW_DEFINITION_IMMUTABLE(40906, "该模板版本为已发布/已归档状态，只读；如需变更请基于它发布新版本", 409),
    /** 409：同一 {@code code} 下已存在草稿版本（templates.md §3.3 同一 code 仅一个可编辑草稿）。 */
    FLOW_DRAFT_ALREADY_EXISTS(40907, "该单据类型已存在草稿版本，请先发布该草稿后再新增版本", 409),

    // ---------- 429 ----------
    TOO_MANY_REQUESTS(42901, "请求过于频繁，请稍后重试", 429),

    // ---------- 5xx ----------
    INTERNAL_ERROR(50000, "服务器内部错误，请联系管理员并提供追踪号", 500),
    AUDIT_WRITE_FAILED(50001, "审计日志写入失败", 500),
    IMMUTABLE_TRIGGER_FAILED(50002, "不可篡改触发器初始化失败", 500),
    /**
     * 500：敏感字段加密密钥不可用（缺失 / 长度不符 / 未知 keyId）。
     *
     * <p><b>绝不静默降级为明文</b>（PRD §5.3 + REQ-NFR-005）：密钥缺失在**启动期**即 fail-fast，
     * 该错误码只用于「运行期遇到未知 keyId 的历史密文」这类必须显式暴露的情形。
     */
    CRYPTO_KEY_UNAVAILABLE(50003, "加密密钥不可用，请检查密钥配置", 500),
    SERVICE_UNAVAILABLE(50301, "服务暂不可用，请稍后重试", 503);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /** 映射的 HTTP 状态码（401 / 403 / 409 …）。 */
    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /** 是否为认证类错误（401xx）。 */
    public boolean isAuthentication() {
        return code >= 40100 && code < 40200;
    }

    /** 是否为授权类错误（403xx）。 */
    public boolean isAuthorization() {
        return code >= 40300 && code < 40400;
    }

    /** 是否为冲突类错误（409xx）。 */
    public boolean isConflict() {
        return code >= 40900 && code < 41000;
    }

    public static ErrorCode of(int code) {
        return Arrays.stream(values())
                .filter(item -> item.code == code)
                .findFirst()
                .orElse(INTERNAL_ERROR);
    }
}
