package com.oa.identity.api;

import com.oa.common.api.ApiResponse;
import com.oa.common.api.PageResult;
import com.oa.common.audit.Audited;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.identity.api.dto.AuthDtos;
import com.oa.identity.api.dto.DirectoryDtos;
import com.oa.identity.api.dto.InFlightDtos;
import com.oa.identity.api.dto.LeaderDtos;
import com.oa.identity.api.dto.PositionDtos;
import com.oa.identity.api.dto.UserDtos;
import com.oa.identity.app.ForceReasonPolicy;
import com.oa.identity.app.OrgLeaderService;
import com.oa.identity.app.PositionService;
import com.oa.identity.app.UserService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人员（{@code /api/v1/identity/users/**}）—— 路径与 normify 基线
 * {@code oa.identity.user.*} / {@code oa.identity.position.*} 逐字一致。
 *
 * <ul>
 *   <li>{@code GET /users}（分页 + 关键字 + 组织/状态筛选）、{@code POST /users}、{@code PUT /users/{id}}</li>
 *   <li>{@code GET /users/{id}/positions}、{@code POST /users/{id}/positions}、
 *       {@code PUT /users/{id}/positions/{positionId}}（设为主岗/改岗位名）、
 *       {@code DELETE /users/{id}/positions/{positionId}}</li>
 *   <li>{@code POST /users/{id}/resign}、{@code /transfer}、{@code /handover}</li>
 *   <li>{@code GET /users/{id}/pending-tasks}（AC-12 的「未处理任务数量」）</li>
 *   <li>{@code GET /users/{id}/in-flight-check}（影响清单：待办数 + 在途数 + 明细）</li>
 *   <li>{@code GET /users/export}（人员主数据 CSV，九列，**仅系统管理员**）</li>
 *   <li>{@code GET /users/{id}/leader-of}</li>
 *   <li>{@code GET /users/me/watermark}（「姓名 + 工号」水印载荷）</li>
 * </ul>
 *
 * <h2>分页参数名（规范）</h2>
 * <b>规范名是 {@code size}</b>（{@code GET /users?page=1&size=20}）；
 * 旧别名 {@code pageSize} 仍被接受（{@code GET /users?page=1&pageSize=20}），
 * **两者同时出现时以 {@code size} 为准**；都不传时默认 20，上限 500（服务层收敛）。
 *
 * <h2>危险操作的留痕（AC-52）</h2>
 * {@code PUT /users/{id}}（切停用）、{@code resign}/{@code transfer} 的请求体含 {@code reason}/{@code force}：
 * {@code force=true} 时必须非空 reason（否则 400）、调用人须为系统管理员（否则 403），
 * 两者随 {@code @Audited} 落 {@code sys_log}。业务裁定：通过准入的 {@code force} 会**放行**
 * {@code oa.identity.block-on-inflight} 的默认阻断（AC-12 的默认口径不变，仅被显式覆盖），
 * 覆盖时服务层补运行日志（双留痕）；授权校验只在控制器，服务层不重复做角色判断。
 *
 * <p><b>审计与口令</b>：新增人员用 {@code recordAfter=false}——
 * 响应体含**仅本次返回**的初始口令，绝不能进审计日志
 * （{@code AuditAspect} 的脱敏键表只覆盖 {@code password}/{@code newPassword} 等，
 * 不含 {@code initialPassword}，故必须显式关闭 after 记录）。
 */
@RestController
@RequestMapping("/api/v1/identity/users")
public class UserController {

    /** 分页默认条数（规范名 {@code size}）。 */
    static final long DEFAULT_PAGE_SIZE = 20L;

    private final UserService userService;
    private final PositionService positionService;
    private final OrgLeaderService leaderService;

    public UserController(UserService userService, PositionService positionService, OrgLeaderService leaderService) {
        this.userService = userService;
        this.positionService = positionService;
        this.leaderService = leaderService;
    }

    // ------------------------------------------------------------------ 人员

    /**
     * 人员列表。
     *
     * @param size     **规范名**：每页条数
     * @param pageSize 旧别名（兼容用）；与 {@code size} 同时出现时以 {@code size} 为准
     */
    @GetMapping
    public ApiResponse<PageResult<UserDtos.UserView>> page(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "orgId", required = false) Long orgId,
            @RequestParam(name = "includeSubOrg", required = false, defaultValue = "true") Boolean includeSubOrg,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "companyId", required = false) Long companyId,
            @RequestParam(name = "page", required = false, defaultValue = "1") long page,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "pageSize", required = false) Long pageSize) {
        return ApiResponse.success(userService.page(keyword, orgId, includeSubOrg, status, companyId, page,
                resolvePageSize(size, pageSize)));
    }

    /**
     * 分页条数解析：规范名 {@code size} 优先，旧别名 {@code pageSize} 兜底，都缺省为
     * {@link #DEFAULT_PAGE_SIZE}（≤0 与超过上限的收敛由服务层完成）。
     */
    static long resolvePageSize(Long size, Long pageSize) {
        if (size != null) {
            return size;
        }
        if (pageSize != null) {
            return pageSize;
        }
        return DEFAULT_PAGE_SIZE;
    }

    @PostMapping
    @Audited(action = "create", targetType = "user", recordArgs = true, recordAfter = false)
    public ApiResponse<UserDtos.UserCreatedView> create(@Valid @RequestBody UserDtos.UserCreateRequest request) {
        return ApiResponse.success(userService.create(request));
    }

    /**
     * 修改人员档案（含 {@code active ⇄ disabled} 状态切换；{@code resigned} 只走 {@code /resign}）。
     *
     * <p>切到停用时与离职同一口径：先在途/待办检查 + {@code oa.identity.block-on-inflight}。
     * 请求体可选 {@code {reason?, force?}}（AC-52）：{@code force=true} 时必须非空 reason（否则 400）
     * 且调用人须为系统管理员（否则 403）；通过准入的 {@code force} **会放行**该在途/待办阻断
     * （覆盖时双留痕：审计切面落 {@code sys_log} + 服务层运行日志）。
     * <b>授权校验只在本控制器</b>（{@link ForceReasonPolicy}），服务层只负责放行语义。
     */
    @PutMapping("/{id}")
    @Audited(action = "update", targetType = "user", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<UserDtos.UserView> update(@PathVariable("id") Long id,
                                                 @Valid @RequestBody UserDtos.UserUpdateRequest request) {
        ForceReasonPolicy.assertAllowed(request.force(), request.reason(), "修改人员档案（切到停用）");
        return ApiResponse.success(userService.update(id, request, request.force(), request.reason()));
    }

    // ------------------------------------------------------------------ 离职 / 调岗 / 交接

    /**
     * 离职（AC-12）。请求体可选：{@code {reason?, force?}}。
     *
     * <p>{@code force=true} 时必须非空 reason（否则 400）且调用人须为系统管理员（否则 403），
     * 两者随 {@code @Audited} 落 {@code sys_log}。业务裁定：通过准入的 {@code force}
     * **会放行**待办/在途阻断（AC-12 的默认阻断不变，仅被显式覆盖），覆盖时服务层输出运行日志。
     */
    @PostMapping("/{id}/resign")
    @Audited(action = "update", targetType = "user", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<UserDtos.ResignResult> resign(@PathVariable("id") Long id,
                                                     @RequestBody(required = false) UserDtos.ResignRequest request) {
        ForceReasonPolicy.assertAllowed(request == null ? null : request.force(),
                request == null ? null : request.reason(), "办理离职");
        return ApiResponse.success(userService.resign(id, request,
                request == null ? null : request.force(), request == null ? null : request.reason()));
    }

    /** 调岗；请求体含 {@code reason}/{@code force}（AC-52 留痕，口径同 {@link #resign}）。 */
    @PostMapping("/{id}/transfer")
    @Audited(action = "update", targetType = "user", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<UserDtos.TransferResult> transfer(@PathVariable("id") Long id,
                                                         @Valid @RequestBody(required = false)
                                                         UserDtos.TransferRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "请求体不能为空：targetOrgId（目标组织）为必填字段");
        }
        ForceReasonPolicy.assertAllowed(request.force(), request.reason(), "办理调岗");
        return ApiResponse.success(userService.transfer(id, request));
    }

    @PostMapping("/{id}/handover")
    @Audited(action = "handover", targetType = "user", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<UserDtos.HandoverResult> handover(@PathVariable("id") Long id,
                                                         @Valid @RequestBody UserDtos.HandoverRequest request) {
        return ApiResponse.success(userService.handover(id, request));
    }

    @GetMapping("/{id}/pending-tasks")
    public ApiResponse<List<UserDtos.PendingTaskView>> pendingTasks(@PathVariable("id") Long id) {
        return ApiResponse.success(userService.pendingTasks(id));
    }

    /**
     * 人员影响清单（离职/调岗/停用前的二次确认）。
     *
     * <p>{@code {pendingTaskCount, inFlightInstanceCount, items:[{instanceId,bizNo,formType,
     * nodeName,initiatorName,currentNodeName,status}]}} —— 比 {@code /pending-tasks} 更全；
     * 数据来源同为 {@code InFlightChecker} 端口，流程表未落地时 {@code items} 为空数组。
     */
    @GetMapping("/{id}/in-flight-check")
    public ApiResponse<InFlightDtos.UserInFlightCheckView> inFlightCheck(@PathVariable("id") Long id) {
        return ApiResponse.success(userService.inFlightCheck(id));
    }

    // ------------------------------------------------------------------ 岗位（一人多岗）

    @GetMapping("/{id}/positions")
    public ApiResponse<List<PositionDtos.PositionView>> positions(@PathVariable("id") Long id) {
        return ApiResponse.success(positionService.positions(id));
    }

    @PostMapping("/{id}/positions")
    @Audited(action = "create", targetType = "user_position", targetId = "#id", recordArgs = true)
    public ApiResponse<PositionDtos.PositionView> addPosition(@PathVariable("id") Long id,
                                                              @Valid @RequestBody PositionDtos.PositionCreateRequest request) {
        return ApiResponse.success(positionService.add(id, request));
    }

    /**
     * 修改任职 / 设为主岗（请求体 {@code {postName?, isPrimary?, remark?}}；
     * 旧别名 {@code position} 亦可，规范名 {@code postName} 优先）。
     *
     * <p>设为主岗时同事务把旧主岗置 0 并回填 {@code sys_user.org_id/position}（T-12）。
     */
    @PutMapping("/{id}/positions/{positionId}")
    @Audited(action = "update", targetType = "user_position", targetId = "#positionId", recordBefore = true, recordArgs = true)
    public ApiResponse<PositionDtos.PositionView> updatePosition(
            @PathVariable("id") Long id,
            @PathVariable("positionId") Long positionId,
            @Valid @RequestBody PositionDtos.PositionUpdateRequest request) {
        return ApiResponse.success(positionService.update(id, positionId, request));
    }

    @DeleteMapping("/{id}/positions/{positionId}")
    @Audited(action = "delete", targetType = "user_position", targetId = "#positionId", recordBefore = true)
    public ApiResponse<AuthDtos.MessageResponse> removePosition(@PathVariable("id") Long id,
                                                                @PathVariable("positionId") Long positionId) {
        positionService.remove(id, positionId);
        return ApiResponse.success(new AuthDtos.MessageResponse("任职已解除"));
    }

    // ------------------------------------------------------------------ 导出

    /**
     * 人员主数据导出（CSV）：**仅系统管理员**（import-spec §9.2 T-11）。
     *
     * <p>列与顺序逐字为 {@code account,employee_no,name,phone,email,company_path,dept_path,status,remark}
     * （§9.1 的 {@code user.csv} 九列）；UTF-8 带 BOM，保证「导出 → 再导入」往返（AC-57）。
     * 非系统管理员 → 403（{@code EXPORT_DENIED}）。
     * {@code recordAfter=false}：CSV 内容过大，审计只记操作人与入参。
     */
    @GetMapping("/export")
    @Audited(action = "export", targetType = "user_import", recordArgs = true, recordAfter = false)
    public ResponseEntity<byte[]> export(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "orgId", required = false) Long orgId,
            @RequestParam(name = "includeSubOrg", required = false, defaultValue = "true") Boolean includeSubOrg,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "companyId", required = false) Long companyId) {
        String csv = userService.exportCsv(keyword, orgId, includeSubOrg, status, companyId);
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("user.csv", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    // ------------------------------------------------------------------ 负责人反查 / 水印

    @GetMapping("/{id}/leader-of")
    public ApiResponse<List<LeaderDtos.LeaderOfView>> leaderOf(@PathVariable("id") Long id) {
        return ApiResponse.success(leaderService.leaderOf(id));
    }

    @GetMapping("/me/watermark")
    public ApiResponse<DirectoryDtos.WatermarkView> myWatermark() {
        return ApiResponse.success(userService.myWatermark());
    }
}
