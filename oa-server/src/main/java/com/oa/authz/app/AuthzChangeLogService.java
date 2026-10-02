package com.oa.authz.app;

import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.infra.AuthzLogMapper;
import com.oa.common.api.PageResult;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 权限变更留痕查询（{@code GET /api/v1/authz/change-logs}，REQ-LOG-004 / AC-59）。
 *
 * <p>数据来自**只追加**的 {@code sys_log}：写入侧由各 Controller 的 {@code @Audited}
 * （{@code recordBefore=true} 记录变更前值，返回值记录变更后值）完成，本服务只读。
 *
 * <p>可见性：系统管理员可查全部；分公司流程管理员只能查**自己**的操作
 * （跨公司的历史操作不在其管理范围内，避免把审计接口变成跨公司探测面）。
 */
@Service
public class AuthzChangeLogService {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 500L;

    private final AuthzLogMapper logMapper;
    private final AuthzOperatorProvider operatorProvider;

    public AuthzChangeLogService(AuthzLogMapper logMapper, AuthzOperatorProvider operatorProvider) {
        this.logMapper = logMapper;
        this.operatorProvider = operatorProvider;
    }

    /** 分页查询（按 id 倒序 = 最近优先）。 */
    public PageResult<AuthzDtos.ChangeLogView> page(long page, Long size, String action, String targetType, Long userId) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        if (!operator.isSuperAdmin() && !operator.isCompanyAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看权限变更日志");
        }
        long effectivePage = page <= 0 ? 1L : page;
        long effectiveSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Long effectiveUserId = operator.isSuperAdmin() ? userId : operator.userId();

        String normalizedAction = blankToNull(action);
        String normalizedTarget = blankToNull(targetType);
        long total = logMapper.count(normalizedAction, normalizedTarget, effectiveUserId);
        List<AuthzDtos.ChangeLogView> records = new ArrayList<>();
        if (total > 0) {
            long offset = (effectivePage - 1) * effectiveSize;
            for (AuthzLogMapper.AuthzLogRow row : logMapper.selectPage(normalizedAction, normalizedTarget,
                    effectiveUserId, offset, effectiveSize)) {
                records.add(new AuthzDtos.ChangeLogView(row.getId(), row.getUserId(), row.getUserName(), row.getAction(),
                        row.getTargetType(), row.getTargetId(), row.getBeforeJson(), row.getAfterJson(), row.getIp(),
                        row.getCreatedAt() == null ? null : TIMESTAMP.format(row.getCreatedAt())));
            }
        }
        return PageResult.of(records, total, effectivePage, effectiveSize);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
