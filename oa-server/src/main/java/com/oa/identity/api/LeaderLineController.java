package com.oa.identity.api;

import com.oa.common.api.ApiResponse;
import com.oa.common.audit.Audited;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.identity.api.dto.LeaderDtos;
import com.oa.identity.app.ForceReasonPolicy;
import com.oa.identity.app.OrgLeaderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集团层业务线分管领导（{@code /api/v1/identity/leaders/**}）。
 *
 * <ul>
 *   <li>{@code GET /leaders/lines}：各业务线分管领导（事项类别五值全部返回）；</li>
 *   <li>{@code PUT /leaders/lines/{category}}：设置某业务线的分管领导（**替换语义**），
 *       {@code category} 接受事项类别 code 或中文标签（{@code 经营/经济/行政/人力/投资}），
 *       落 {@code sys_org_leader.category}；业务线绑定与普通负责人互不冲突。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/identity/leaders")
public class LeaderLineController {

    private final OrgLeaderService leaderService;

    public LeaderLineController(OrgLeaderService leaderService) {
        this.leaderService = leaderService;
    }

    @GetMapping("/lines")
    public ApiResponse<List<LeaderDtos.LeaderLineView>> lines() {
        return ApiResponse.success(leaderService.lines());
    }

    /**
     * 设置某业务线的分管领导（替换语义）。
     *
     * <p>请求体沿用 {@link LeaderDtos.LeaderLineUpsertRequest}，并**追加**可选的
     * {@code reason}/{@code force}（AC-52：绑/解绑分管领导直接影响审批人解析）：
     * {@code force=true} 时必须非空 reason（否则 400）且调用人须为系统管理员（否则 403）；
     * 两者随 {@code @Audited(recordArgs=true)} 写进 {@code sys_log}。
     */
    @PutMapping("/lines/{category}")
    @Audited(action = "update", targetType = "org_leader", recordBefore = true, recordArgs = true)
    public ApiResponse<LeaderDtos.LeaderView> setLine(
            @PathVariable("category") String category,
            @Valid @RequestBody(required = false) LeaderDtos.LeaderLineUpsertRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求体不能为空：userId（分管领导）为必填字段");
        }
        ForceReasonPolicy.assertAllowed(request.force(), request.reason(), "绑定业务线分管领导");
        return ApiResponse.success(leaderService.setLine(category, request));
    }
}
