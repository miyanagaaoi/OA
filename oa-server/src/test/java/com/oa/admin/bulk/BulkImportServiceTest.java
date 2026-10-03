package com.oa.admin.bulk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.identity.app.InFlightChecker;
import com.oa.identity.domain.SysOrg;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 批量导入编排单测（阶段 1.8，{@code BulkImportService}）。
 *
 * <p>覆盖 import-spec 的**闸门语义**（不依赖数据库，用 Mockito 桩住索引与锁）：
 * <ol>
 *   <li><b>权限</b>：普通员工 / 部门负责人调用 → 403；系统管理员与分公司流程管理员放行；</li>
 *   <li><b>错误零落库</b>：文件级 error（缺 BOM / 表头不符）→ 报告 {@code ok=false} 且**不触碰任何写入**；</li>
 *   <li><b>dry-run 不写库</b>：preview 只校验；</li>
 *   <li><b>并发</b>：已持锁时再导入 → 409 {@code IMPORT_IN_PROGRESS}；</li>
 *   <li><b>锁总是被释放</b>（成功/失败路径都不泄漏）。</li>
 * </ol>
 */
class BulkImportServiceTest {

    private StringRedisTemplate redisTemplate;

    private ImportLookup lookup;

    private ImportLockService lockService;

    private ImportStrategy strategy;

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    /** 构造被测服务（策略与锁按需替换）。 */
    private BulkImportService service(ImportStrategy... strategies) {
        redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        // 模拟真实 Redis：SET NX 写入令牌，随后 GET 能读回同一令牌 → 释放时按 token 比对删除
        java.util.concurrent.atomic.AtomicReference<String> holder = new java.util.concurrent.atomic.AtomicReference<>();
        when(ops.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenAnswer(invocation -> {
            holder.set(invocation.getArgument(1));
            return Boolean.TRUE;
        });
        when(ops.get(anyString())).thenAnswer(invocation -> holder.get());
        lookup = mock(ImportLookup.class);
        ImportLookup.Snapshot snapshot = mock(ImportLookup.Snapshot.class);
        when(snapshot.orgsById()).thenReturn(Map.of());
        when(snapshot.roleCodes()).thenReturn(List.of("admin", "employee"));
        when(lookup.snapshot()).thenReturn(snapshot);
        lockService = new ImportLockService(redisTemplate);
        List<ImportStrategy> beans = strategies.length == 0
                ? List.of(mockStrategy(ImportKind.ORG))
                : List.of(strategies);
        return new BulkImportService(beans, lookup, lockService, mock(InFlightChecker.class));
    }

    private ImportStrategy mockStrategy(ImportKind kind) {
        strategy = mock(ImportStrategy.class);
        when(strategy.kind()).thenReturn(kind);
        return strategy;
    }

    private static void authenticate(String... roles) {
        Set<DataScopeType> scopes = EnumSet.of(DataScopeType.GROUP_ALL);
        CurrentUser principal = CurrentUser.of(1L, "admin01", "管理员", "A0001", 1L, 1L, Set.of(roles), scopes, false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(Set.of(roles))
                .scopes(scopes)
                .build());
    }

    private static byte[] orgFile(boolean bom) {
        String body = String.join(",", ImportKind.ORG.columns()) + "\r\n"
                + "集团,集团,集团,,启用,\r\n";
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        if (!bom) {
            return raw;
        }
        byte[] result = new byte[raw.length + 3];
        result[0] = (byte) 0xEF;
        result[1] = (byte) 0xBB;
        result[2] = (byte) 0xBF;
        System.arraycopy(raw, 0, result, 3, raw.length);
        return result;
    }

    @Test
    @DisplayName("权限：非管理员/非分公司管理员 → 403，且不触碰策略")
    void nonAdminIsRejected() {
        ImportStrategy mocked = mockStrategy(ImportKind.ORG);
        BulkImportService service = service(mocked);
        authenticate("employee");

        assertThatThrownBy(() -> service.preview(ImportKind.ORG, orgFile(true)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(mocked, never()).validate(any(), any(), any());
    }

    @Test
    @DisplayName("preview：缺 BOM（E-ENC-001）仍跑完整校验（阶段 A 一次列全错误），但不写库")
    void previewWithMissingBomStillValidatesButNeverWrites() {
        ImportStrategy mocked = mockStrategy(ImportKind.ORG);
        when(mocked.validate(any(), any(), any())).thenReturn(new ArrayList<>());
        BulkImportService service = service(mocked);
        authenticate("admin");

        ImportReport report = service.preview(ImportKind.ORG, orgFile(false));

        assertThat(report.isOk()).isFalse();
        assertThat(report.getErrors()).isGreaterThan(0);
        assertThat(report.isDryRun()).isTrue();
        assertThat(report.getSuggestions()).isNotEmpty();
        assertThat(report.getSuggestions().get(0)).containsKeys("code", "count", "suggestion");
        verify(mocked).validate(any(), any(), any());
        verify(mocked, never()).write(any(), any(), any());
        verify(redisTemplate).delete(ImportLockService.LOCK_KEY);
    }

    @Test
    @DisplayName("preview：表头不符（E-HDR-001）→ 文件级拒绝，策略与锁都不参与")
    void previewWithHeaderErrorStopsAtFileLevel() {
        ImportStrategy mocked = mockStrategy(ImportKind.ORG);
        BulkImportService service = service(mocked);
        authenticate("admin");
        byte[] bad = ("org_name,org_path,org_type,parent_path,status,remark\r\n集团,集团,集团,,启用,\r\n")
                .getBytes(StandardCharsets.UTF_8);

        ImportReport report = service.preview(ImportKind.ORG, bad);

        assertThat(report.isOk()).isFalse();
        assertThat(report.getFindings()).anySatisfy(finding ->
                assertThat(finding.code()).isEqualTo("E-HDR-001"));
        verify(mocked, never()).validate(any(), any(), any());
        verify(mocked, never()).write(any(), any(), any());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("preview：校验通过时仍不落库（dryRun=true），且锁被释放")
    void previewReleasesLockAndDoesNotWrite() {
        ImportStrategy mocked = mockStrategy(ImportKind.ORG);
        when(mocked.validate(any(), any(), any())).thenReturn(new ArrayList<>());
        BulkImportService service = service(mocked);
        authenticate("company_admin");

        ImportReport report = service.preview(ImportKind.ORG, orgFile(true));

        assertThat(report.isOk()).isTrue();
        assertThat(report.getAdded()).isZero();
        assertThat(report.getUpdated()).isZero();
        verify(mocked, never()).write(any(), any(), any());
        verify(redisTemplate).delete(ImportLockService.LOCK_KEY);
    }

    @Test
    @DisplayName("commit：存在 error → 整批拒绝、策略 write 不被调用（错误零落库），锁仍被释放")
    void commitWithErrorsWritesNothing() {
        ImportStrategy mocked = mockStrategy(ImportKind.ORG);
        when(mocked.validate(any(), any(), any())).thenAnswer(invocation -> {
            ImportReport report = invocation.getArgument(2);
            report.add(ImportFinding.error("E-ORG-002", "org.csv", 3, "parent_path", "集团/公司B",
                    "父路径不存在"));
            return new ArrayList<>();
        });
        BulkImportService service = service(mocked);
        authenticate("admin");

        ImportReport report = service.commit(ImportKind.ORG, orgFile(true));

        assertThat(report.isOk()).isFalse();
        assertThat(report.getFailedRows()).isEqualTo(1);
        assertThat(report.getNotes()).anySatisfy(note -> assertThat(note).contains("错误零落库"));
        verify(mocked, never()).write(any(), any(), any());
        verify(redisTemplate).delete(ImportLockService.LOCK_KEY);
    }

    @Test
    @DisplayName("commit：校验通过 → 调用策略落库并回填统计；报表回显新增/更新/跳过")
    void commitWritesAndReportsStats() {
        ImportStrategy mocked = mockStrategy(ImportKind.ORG);
        when(mocked.validate(any(), any(), any())).thenReturn(new ArrayList<>());
        org.mockito.stubbing.Answer<Void> answer = invocation -> {
            ImportReport report = invocation.getArgument(2);
            report.stats(2, 1, 3);
            return null;
        };
        org.mockito.Mockito.doAnswer(answer).when(mocked).write(any(), any(), any());
        BulkImportService service = service(mocked);
        authenticate("admin");

        ImportReport report = service.commit(ImportKind.ORG, orgFile(true));

        assertThat(report.isOk()).isTrue();
        assertThat(report.getAdded()).isEqualTo(2);
        assertThat(report.getUpdated()).isEqualTo(1);
        assertThat(report.getSkipped()).isEqualTo(3);
        assertThat(report.getTotalRows()).isEqualTo(1);
        verify(mocked).write(any(), any(), any());
    }

    @Test
    @DisplayName("并发：已有导入任务进行中 → 409 IMPORT_IN_PROGRESS")
    void concurrentImportIsRejected() {
        redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(Boolean.FALSE);
        ImportLockService busy = new ImportLockService(redisTemplate);
        ImportStrategy mocked = mockStrategy(ImportKind.ORG);
        BulkImportService service = new BulkImportService(List.of(mocked), lookup, busy,
                mock(InFlightChecker.class));
        authenticate("admin");

        assertThatThrownBy(() -> service.preview(ImportKind.ORG, orgFile(true)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.IMPORT_IN_PROGRESS));
    }

    @Test
    @DisplayName("数据域闸门：allowAll 放行一切；无前缀（普通员工口径）一律拒绝并给出 E-XXX-020")
    void scopeGuardFailClosed() {
        assertThat(ImportScopeGuard.allowAll().allowsPath("/1/2/")).isTrue();

        SysOrg company = new SysOrg();
        company.setId(12L);
        company.setPath("/1/12/");
        DataScopeContext context = DataScopeContext.builder()
                .principal(CurrentUser.of(5L, "ca01", "分管理员", "A0005", 12L, 12L,
                        Set.of("company_admin"), EnumSet.of(DataScopeType.COMPANY), false))
                .scopes(EnumSet.of(DataScopeType.COMPANY))
                .companyId(12L)
                .build();
        ImportScopeGuard guard = ImportScopeGuard.of(context, Map.of(12L, company));

        assertThat(guard.allowsPath("/1/12/")).isTrue();
        assertThat(guard.allowsPath("/1/12/135/")).isTrue();
        assertThat(guard.allowsPath("/1/13/")).isFalse();
        assertThat(guard.allowsPath(null)).isFalse();

        List<ImportFinding> findings = new ArrayList<>();
        assertThat(guard.require(ImportKind.ORG, "/1/13/", 4, "parent_path", "/1/13/", findings)).isFalse();
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).code()).isEqualTo("E-ORG-020");
        assertThat(findings.get(0).line()).isEqualTo(4);
    }
}
