package com.oa.identity.api;

import com.oa.common.api.ApiResponse;
import com.oa.common.audit.Audited;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.identity.api.dto.AuthDtos;
import com.oa.identity.api.dto.LeaderDtos;
import com.oa.identity.api.dto.OrgDtos;
import com.oa.identity.app.ForceReasonPolicy;
import com.oa.identity.app.OrgLeaderService;
import com.oa.identity.app.OrgService;
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
 * 组织（{@code /api/v1/identity/orgs/**}）—— 路径与 normify 基线 {@code oa.identity.org.*} 逐字一致。
 *
 * <ul>
 *   <li>{@code GET  /orgs/tree}、{@code /orgs/search}、{@code /orgs/selector}（支持 {@code keyword} 服务端过滤，返回仍是树）</li>
 *   <li>{@code POST /orgs}、{@code PUT /orgs/{id}}、{@code POST /orgs/{id}/move}</li>
 *   <li>{@code POST /orgs/{id}/disable}、{@code /enable}</li>
 *   <li>{@code GET  /orgs/{id}/path}、{@code /ancestors}、{@code /descendants}、{@code /in-flight-check}</li>
 *   <li>{@code GET  /orgs/{id}/leaders}、{@code POST /orgs/{id}/leaders}、
 *       {@code PUT/DELETE /orgs/{id}/leaders/{leaderId}}、{@code GET /orgs/{id}/leader-candidates}（支持 {@code keyword}）</li>
 *   <li>{@code GET  /orgs/export}（仅系统管理员；CSV 带 BOM，列与 import-spec §9.1 一致）</li>
 * </ul>
 *
 * <h2>本次补齐的契约缺口</h2>
 * <ol>
 *   <li>{@code hasPrimaryLeader}：树/平铺节点直接标示「未设正职」（AC-11），
 *       由 {@code sys_org_leader} 的**一次批量查询**聚合，不做逐节点查询；</li>
 *   <li>{@code /orgs/{id}/in-flight-check} 追加 {@code inFlightInstanceCount} /
 *       {@code pendingTaskCount} / {@code activeStaffCount} / {@code items}，既有字段名不变；</li>
 *   <li>危险操作（move/disable/enable/解绑负责人）接受可选 {@code {reason?, force?}} 并留痕（AC-52）；
 *       {@code force=true}（系统管理员 + 必填原因）在 {@code /disable} 上**放行**在途/待办阻断
 *       （原阻断语义不变，仅被显式覆盖），其余动作无在途判定、force 仅留痕。</li>
 * </ol>
 *
 * <p>审计：组织增删改/移动/启停、负责人绑定变更、导出全部标 {@link Audited}。
 */
@RestController
@RequestMapping("/api/v1/identity/orgs")
public class OrgController {

    private final OrgService orgService;
    private final OrgLeaderService leaderService;

    public OrgController(OrgService orgService, OrgLeaderService leaderService) {
        this.orgService = orgService;
        this.leaderService = leaderService;
    }

    // ------------------------------------------------------------------ 查询

    @GetMapping("/tree")
    public ApiResponse<List<OrgDtos.OrgView>> tree(
            @RequestParam(name = "rootId", required = false) Long rootId,
            @RequestParam(name = "includeDisabled", required = false, defaultValue = "true") boolean includeDisabled) {
        return ApiResponse.success(orgService.tree(rootId, includeDisabled));
    }

    @GetMapping("/search")
    public ApiResponse<List<OrgDtos.OrgView>> search(
            @RequestParam(name = "keyword") String keyword,
            @RequestParam(name = "includeDisabled", required = false, defaultValue = "true") boolean includeDisabled) {
        return ApiResponse.success(orgService.search(keyword, includeDisabled));
    }

    /**
     * 选择器数据源（精简树）。
     *
     * <p>{@code keyword} 为**服务端过滤**（施工要求第 9 条）：命中节点连同其祖先链返回，
     * 结构仍是树形，前端无需本地过滤。
     */
    @GetMapping("/selector")
    public ApiResponse<List<OrgDtos.OrgOption>> selector(
            @RequestParam(name = "rootId", required = false) Long rootId,
            @RequestParam(name = "includeDisabled", required = false, defaultValue = "false") boolean includeDisabled,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return ApiResponse.success(orgService.selector(rootId, includeDisabled, keyword));
    }

    @GetMapping("/{id}/path")
    public ApiResponse<OrgDtos.OrgPathView> path(@PathVariable("id") Long id) {
        return ApiResponse.success(orgService.path(id));
    }

    @GetMapping("/{id}/ancestors")
    public ApiResponse<List<OrgDtos.OrgView>> ancestors(@PathVariable("id") Long id) {
        return ApiResponse.success(orgService.ancestors(id));
    }

    @GetMapping("/{id}/descendants")
    public ApiResponse<List<OrgDtos.OrgView>> descendants(
            @PathVariable("id") Long id,
            @RequestParam(name = "includeDisabled", required = false, defaultValue = "true") boolean includeDisabled) {
        return ApiResponse.success(orgService.descendants(id, includeDisabled));
    }

    @GetMapping("/{id}/in-flight-check")
    public ApiResponse<OrgDtos.InFlightCheckView> inFlightCheck(@PathVariable("id") Long id) {
        return ApiResponse.success(orgService.inFlightCheck(id));
    }

    // ------------------------------------------------------------------ 组织变更

    @PostMapping
    @Audited(action = "create", targetType = "org", recordArgs = true)
    public ApiResponse<OrgDtos.OrgView> create(@Valid @RequestBody OrgDtos.OrgCreateRequest request) {
        return ApiResponse.success(orgService.create(request));
    }

    @PutMapping("/{id}")
    @Audited(action = "update", targetType = "org", targetId = "#id", recordBefore = true)
    public ApiResponse<OrgDtos.OrgView> update(@PathVariable("id") Long id,
                                               @Valid @RequestBody OrgDtos.OrgUpdateRequest request) {
        return ApiResponse.success(orgService.update(id, request));
    }

    /**
     * 移动节点。
     *
     * <p>{@code reason}/{@code force} 由 {@link OrgDtos.OrgMoveRequest} 承载（AC-52 留痕）：
     * {@code force=true} 时必须非空 reason（否则 400）且须系统管理员（否则 403）；
     * 两者随 {@code @Audited(recordArgs/recordBefore)} 写进 {@code sys_log}。
     * 移动**不做**在途阻断（PRD §5.4「在途单据一律不变」），{@code force} 仅随入参与日志留痕。
     */
    @PostMapping("/{id}/move")
    @Audited(action = "move", targetType = "org", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<OrgDtos.MoveResult> move(@PathVariable("id") Long id,
                                                @Valid @RequestBody(required = false) OrgDtos.OrgMoveRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "请求体不能为空：newParentId 为该接口的既有字段（挂为根时请显式传 null）");
        }
        ForceReasonPolicy.assertAllowed(request.force(), request.reason(), "移动组织节点");
        return ApiResponse.success(orgService.move(id, request, request.force(), request.reason()));
    }

    /**
     * 停用节点（危险操作）。
     *
     * <p>请求体**可选**：{@code {reason?, force?}}（AC-52）。旧调用不带 body 仍然可用；
     * {@code force=true} 时必须非空 reason（否则 400）且调用人须为系统管理员（否则 403）。
     * 业务裁定：通过准入的 {@code force} **会放行**在途/待办阻断（AC-11/AC-12 的默认阻断不变，
     * 仅被显式覆盖），覆盖时服务层输出运行日志、审计切面落 {@code sys_log}（双留痕）。
     * <b>授权校验只在本控制器</b>（{@link ForceReasonPolicy}），服务层只负责放行语义。
     */
    @PostMapping("/{id}/disable")
    @Audited(action = "disable", targetType = "org", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<OrgDtos.StateResult> disable(@PathVariable("id") Long id,
                                                   @RequestBody(required = false) OrgDtos.StateChangeRequest request) {
        ForceReasonPolicy.assertAllowed(force(request), reason(request), "停用组织节点");
        return ApiResponse.success(orgService.disable(id, force(request), reason(request)));
    }

    /** 启用节点（危险操作；请求体可选 {@code {reason?, force?}}，口径同 {@link #disable}）。 */
    @PostMapping("/{id}/enable")
    @Audited(action = "enable", targetType = "org", targetId = "#id", recordBefore = true)
    public ApiResponse<OrgDtos.StateResult> enable(@PathVariable("id") Long id,
                                                  @RequestBody(required = false) OrgDtos.StateChangeRequest request) {
        ForceReasonPolicy.assertAllowed(force(request), reason(request), "启用组织节点");
        return ApiResponse.success(orgService.enable(id, force(request), reason(request)));
    }

    private static String reason(OrgDtos.StateChangeRequest request) {
        return request == null ? null : request.reason();
    }

    private static Boolean force(OrgDtos.StateChangeRequest request) {
        return request == null ? null : request.force();
    }

    // ------------------------------------------------------------------ 导出

    /**
     * 组织主数据导出（CSV）：**仅系统管理员**（import-spec §9.2 T-11）。
     *
     * <p>列与顺序逐字为 {@code org_path,org_name,org_type,parent_path,status,remark}；
     * 编码 UTF-8 带 BOM，保证「导出 → 再导入」往返（import-spec §9.1）。
     * {@code recordAfter=false}：CSV 内容过大，审计只记操作人与入参。
     */
    @GetMapping("/export")
    @Audited(action = "export", targetType = "org_import", recordArgs = true, recordAfter = false)
    public ResponseEntity<byte[]> export() {
        String csv = orgService.exportCsv();
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("org.csv", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    // ------------------------------------------------------------------ 负责人

    @GetMapping("/{id}/leaders")
    public ApiResponse<List<LeaderDtos.LeaderView>> leaders(@PathVariable("id") Long id) {
        return ApiResponse.success(leaderService.leaders(id));
    }

    @PostMapping("/{id}/leaders")
    @Audited(action = "create", targetType = "org_leader", recordArgs = true)
    public ApiResponse<LeaderDtos.LeaderView> bindLeader(@PathVariable("id") Long id,
                                                         @Valid @RequestBody LeaderDtos.LeaderCreateRequest request) {
        return ApiResponse.success(leaderService.bind(id, request));
    }

    @PutMapping("/{id}/leaders/{leaderId}")
    @Audited(action = "update", targetType = "org_leader", targetId = "#leaderId", recordBefore = true, recordArgs = true)
    public ApiResponse<LeaderDtos.LeaderView> updateLeader(@PathVariable("id") Long id,
                                                           @PathVariable("leaderId") Long leaderId,
                                                           @Valid @RequestBody LeaderDtos.LeaderUpdateRequest request) {
        return ApiResponse.success(leaderService.update(id, leaderId, request));
    }

    /**
     * 解除负责人绑定（危险操作；请求体可选 {@code {reason?, force?}}，口径同 {@link #disable}）。
     *
     * <p>AC-52：解绑会直接改变审批人解析结果（该组织可能因此「未设正职」），
     * 因此原因与强制标记必须随 {@code @Audited} 落 {@code sys_log}。
     */
    @DeleteMapping("/{id}/leaders/{leaderId}")
    @Audited(action = "delete", targetType = "org_leader", targetId = "#leaderId", recordBefore = true, recordArgs = true)
    public ApiResponse<AuthDtos.MessageResponse> unbindLeader(
            @PathVariable("id") Long id,
            @PathVariable("leaderId") Long leaderId,
            @RequestBody(required = false) OrgDtos.StateChangeRequest request) {
        ForceReasonPolicy.assertAllowed(force(request), reason(request), "解除负责人绑定");
        leaderService.unbind(id, leaderId);
        return ApiResponse.success(new AuthDtos.MessageResponse("负责人绑定已解除"));
    }

    /** 负责人候选人；{@code keyword} 为服务端过滤（姓名/账号/工号，施工要求第 9 条）。 */
    @GetMapping("/{id}/leader-candidates")
    public ApiResponse<List<LeaderDtos.LeaderCandidateView>> leaderCandidates(
            @PathVariable("id") Long id,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return ApiResponse.success(leaderService.candidates(id, keyword));
    }
}
