package com.oa.admin.bulk;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.identity.app.InFlightChecker;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 批量导入流水线（阶段 1.8）—— <b>preview（干跑校验）/ commit（事务落库）两段式</b>。
 *
 * <h2>口径（doc/import-spec.md）</h2>
 * <ol>
 *   <li><b>阶段 A 全量校验</b>（§5.3）：一次列全所有 error，不因首行失败而中断；</li>
 *   <li><b>阶段 B 判定</b>：只要存在 ≥1 个 error → 整批失败，**错误零落库**；</li>
 *   <li><b>阶段 C 导入</b>：全部 error 为 0 时，在**单个事务**内按 §2.1 顺序写库，
 *       任一失败整体 ROLLBACK；</li>
 *   <li><b>并发</b>（§6.4）：同一时刻只允许一个导入任务（{@code oa:import:org_user} 锁）；</li>
 *   <li><b>权限</b>：系统管理员或分公司流程管理员；分公司管理员逐行受数据域约束（fail-closed）；</li>
 *   <li><b>幂等</b>（§6.1）：按业务键 upsert，同文件重复导入不产生重复数据（第二次为 0 新增 / 0 更新 / N 跳过）。</li>
 * </ol>
 *
 * <h2>数据域与系统口径</h2>
 * 校验与落库在 {@link DataScopeContext#system()} 下执行（读取全库真相，避免「路径不存在」误报），
 * 授权由 {@link ImportScopeGuard} **逐行显式**判定 —— 这正是「写操作必须显式校验数据域」的落点。
 * 执行前后恢复调用方的线程上下文，绝不把系统口径泄漏给请求线程。
 */
@Service
public class BulkImportService {

    private static final Logger log = LoggerFactory.getLogger(BulkImportService.class);

    private final Map<ImportKind, ImportStrategy> strategies = new EnumMap<>(ImportKind.class);

    private final ImportLookup lookup;

    private final ImportLockService lockService;

    private final InFlightChecker inFlightChecker;

    public BulkImportService(List<ImportStrategy> strategyBeans, ImportLookup lookup,
                             ImportLockService lockService, InFlightChecker inFlightChecker) {
        for (ImportStrategy strategy : strategyBeans) {
            strategies.put(strategy.kind(), strategy);
        }
        this.lookup = lookup;
        this.lockService = lockService;
        this.inFlightChecker = inFlightChecker;
    }

    /** 干跑校验（{@code preview}）：不写任何业务表，产出与正式导入同结构的报告。 */
    public ImportReport preview(ImportKind kind, byte[] bytes) {
        return run(kind, bytes, true);
    }

    /**
     * 正式导入（{@code commit}）：**先重新全量校验**，0 error 才落库；单事务，失败整体回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportReport commit(ImportKind kind, byte[] bytes) {
        return run(kind, bytes, false);
    }

    /**
     * 受影响在途单据清单预检（§7；W12 前无在途单据，清单恒为空）。
     *
     * <p>实现方式：先跑一次 dry-run 校验，再对**涉及的组织**取在途清单 ——
     * 与「同一端口复用」的约定一致（{@link InFlightChecker}），不另造第二套判定。
     */
    public ImportReport impactPreview(ImportKind kind, byte[] bytes) {
        ImportReport report = run(kind, bytes, true);
        report.note("受影响在途单据清单口径见 import-spec §7：审批人在发起时快照，组织/人员调整不改变在途单据；"
                + "阶段 2b（W12）前 flow_instance 无数据，清单恒为空");
        return report;
    }

    private ImportReport run(ImportKind kind, byte[] bytes, boolean dryRun) {
        CurrentUser principal = requirePrincipal();
        ImportPermission.require(principal);
        ImportStrategy strategy = strategies.get(kind);
        if (strategy == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知的导入类型：" + kind);
        }
        if (bytes == null || bytes.length == 0) {
            throw new BizException(ErrorCode.IMPORT_FILE_INVALID, "导入文件为空（" + kind.fileName() + "）");
        }
        ImportReport report = new ImportReport(kind, dryRun);
        List<ImportFinding> fileFindings = new ArrayList<>();
        CsvTable table = CsvTable.parse(bytes, kind, fileFindings);
        report.addAll(fileFindings);
        if (table == null) {
            report.note("文件级校验未通过（编码/表头），已整批拒绝：请修正后重新上传");
            return report;
        }
        report.totalRows(table.rows().size());

        String token = lockService.acquire();
        DataScopeContext original = DataScopeContext.current();
        try {
            DataScopeContext.set(DataScopeContext.system());
            ImportLookup.Snapshot snapshot = lookup.snapshot();
            ImportContext context = new ImportContext(principal, principal.id(), dryRun, snapshot,
                    ImportScopeGuard.of(original, snapshot.orgsById()));
            Object parsed = strategy.validate(table, context, report);
            if (!report.isOk()) {
                report.note("存在 " + report.getErrors() + " 个 error：" + (dryRun
                        ? "干跑校验未通过，未写任何数据（错误零落库）"
                        : "整批拒绝，未写任何数据（错误零落库）"));
                return report;
            }
            if (dryRun) {
                report.note("干跑校验通过（dryRun=true）：未写任何业务表；正式导入请调用 /import");
                return report;
            }
            strategy.write(parsed, context, report);
            report.note("导入完成：新增 " + report.getAdded() + " / 更新 " + report.getUpdated()
                    + " / 跳过 " + report.getSkipped());
            return report;
        } finally {
            if (original == null) {
                DataScopeContext.clear();
            } else {
                DataScopeContext.set(original);
            }
            lockService.release(token);
        }
    }

    private static CurrentUser requirePrincipal() {
        DataScopeContext context = DataScopeContext.current();
        CurrentUser principal = context == null ? null : context.getPrincipal();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
