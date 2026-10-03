package com.oa.admin.bulk.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.admin.bulk.CsvTable;
import com.oa.admin.bulk.ImportContext;
import com.oa.admin.bulk.ImportFinding;
import com.oa.admin.bulk.ImportKind;
import com.oa.admin.bulk.ImportLookup;
import com.oa.admin.bulk.ImportReport;
import com.oa.admin.bulk.ImportScopeGuard;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.identity.app.InFlightChecker;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.infra.SysOrgLeaderMapper;
import com.oa.identity.infra.SysOrgMapper;
import com.oa.identity.infra.SysUserMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code OrgImportStrategy} 的**占位 path 唯一性**回归单测（无 DB：mapper 全部 Mockito 桩住）。
 *
 * <p><b>回归对象</b>：{@code sys_org.path} 是 {@code NOT NULL} 且有唯一键 {@code uk_sys_org_path}，
 * 而真实 path 依赖自增 id，导入只能先写**占位 path**、拿到 id 后再回填。历史实现用
 * {@code "/pending-" + operatorId + "-" + sequence + "/"}：同一运算符的两次导入都从序号 1 起，
 * 并发（或未回填的事务重叠）时**取到同一占位 path → DuplicateKeyException（500）**。
 * 现实现改为与 {@code OrgService#create} 共用的随机串口径
 * （{@code OrgHierarchy#temporaryPath}）。
 *
 * <p>本测试锁死：同一运算符连续两次导入，写入的占位 path **两两不同**、与运算符无关、
 * 且格式就是共享口径（{@code /pending-<32hex>/}）；同时真实 path 仍按 {@code /父/子/} 回填。
 */
class OrgImportStrategyTest {

    private static final long OPERATOR_ID = 10086L;

    private static final long GROUP_ID = 1L;

    private SysOrgMapper orgMapper;

    private SysUserMapper userMapper;

    private SysOrgLeaderMapper leaderMapper;

    private OrgImportStrategy strategy;

    private final AtomicLong ids = new AtomicLong(GROUP_ID - 1);

    /** 每次 insertOrg 落库当时的占位 path（SysOrg 实例随后会被回填真实 path，故必须当场记下）。 */
    private final List<String> placeholders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        orgMapper = mock(SysOrgMapper.class);
        userMapper = mock(SysUserMapper.class);
        leaderMapper = mock(SysOrgLeaderMapper.class);
        strategy = new OrgImportStrategy(orgMapper, userMapper, leaderMapper, mock(InFlightChecker.class));
        when(leaderMapper.selectPrimaryOrgIds()).thenReturn(List.of());
        when(orgMapper.insertOrg(any(SysOrg.class))).thenAnswer(invocation -> {
            SysOrg inserted = invocation.getArgument(0, SysOrg.class);
            placeholders.add(inserted.getPath());
            inserted.setId(ids.incrementAndGet());
            return 1;
        });
    }

    /** CSV 夹具：集团 → 公司A（两行，父行先于子行落库）。 */
    private static byte[] csv() {
        String body = "org_path,org_name,org_type,parent_path,status,remark\r\n"
                + "集团,集团,集团,,启用,\r\n"
                + "集团/公司A,公司A,公司,集团,启用,\r\n";
        byte[] text = body.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[text.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(text, 0, bytes, 3, text.length);
        return bytes;
    }

    private ImportContext context() {
        ImportLookup.Snapshot snapshot = mock(ImportLookup.Snapshot.class);
        when(snapshot.orgsById()).thenReturn(Map.of());
        CurrentUser principal = CurrentUser.of(OPERATOR_ID, "admin", "系统管理员", "10086", GROUP_ID, null,
                Set.of("admin"), EnumSet.of(DataScopeType.GROUP_ALL), false);
        return new ImportContext(principal, OPERATOR_ID, false, snapshot, ImportScopeGuard.allowAll());
    }

    /** 跑一次完整导入（validate → write），返回本次落库报告的 added 行数。 */
    private int runImport(ImportContext context) {
        List<ImportFinding> findings = new ArrayList<>();
        CsvTable table = CsvTable.parse(csv(), ImportKind.ORG, findings);
        ImportReport report = new ImportReport(ImportKind.ORG, false);
        Object parsed = strategy.validate(table, context, report);
        assertThat(report.errors()).as("夹具必须零 error：%s", report.getFindings()).isZero();
        strategy.write(parsed, context, report);
        return report.getAdded();
    }

    @Test
    @DisplayName("导入：同一运算符两次导入的占位 path 两两不同（旧实现 /pending-<operator>-<seq>/ 会撞 uk_sys_org_path）")
    void commitUsesGloballyUniquePlaceholderPath() {
        runImport(context());
        // 第二次导入：同一个 operatorId → 旧实现会再次从 /pending-10086-1/ 开始，必然与第一次冲突
        runImport(context());

        ArgumentCaptor<SysOrg> inserted = ArgumentCaptor.forClass(SysOrg.class);
        verify(orgMapper, times(4)).insertOrg(inserted.capture());

        assertThat(placeholders).doesNotContainNull();
        assertThat(placeholders).as("占位 path 必须全局唯一，才不撞 uk_sys_org_path")
                .doesNotHaveDuplicates().hasSize(4);
        for (String path : placeholders) {
            assertThat(path).matches("^/pending-[0-9a-f]{32}/$");
            // 旧实现形如 /pending-10086-1/：同一运算符的两次导入必然重现该前缀
            assertThat(path).as("占位 path 不得再掺入运算符 id")
                    .doesNotStartWith("/pending-" + OPERATOR_ID + "-");
        }
    }

    @Test
    @DisplayName("导入：占位 path 之后按 /父/子/ 回填真实 path（口径与 OrgService#create 一致）")
    void commitBackfillsRealPath() {
        ImportContext context = context();
        assertThat(runImport(context)).isEqualTo(2);

        verify(orgMapper).updatePathAndDepth(GROUP_ID, "/1/", 1, OPERATOR_ID);
        verify(orgMapper).updatePathAndDepth(GROUP_ID + 1, "/1/2/", 2, OPERATOR_ID);
    }
}
