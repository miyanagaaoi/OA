package com.oa.admin.bulk.strategy;

import com.oa.admin.bulk.CsvTable;
import com.oa.admin.bulk.ImportContext;
import com.oa.admin.bulk.ImportFinding;
import com.oa.admin.bulk.ImportKind;
import com.oa.admin.bulk.ImportReport;
import com.oa.admin.bulk.ImportStrategy;
import com.oa.common.security.PasswordService;
import com.oa.identity.app.InFlightChecker;
import com.oa.identity.app.InitialPasswordGenerator;
import com.oa.identity.app.OrgHierarchy;
import com.oa.identity.app.OrgService;
import com.oa.identity.domain.IdentityEnums;
import com.oa.identity.domain.IdentityEnums.OrgType;
import com.oa.identity.domain.IdentityEnums.UserStatus;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysUser;
import com.oa.identity.infra.SysUserMapper;
import com.oa.platform.security.crypto.PhoneCryptoService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 第 ② 步：人员导入（{@code user.csv} → {@code sys_user}，import-spec §3.3 / §4.3 / §6.1）。
 *
 * <p>关键实现点：
 * <ol>
 *   <li><b>业务键</b>：{@code account}；存在则更新档案，<b>{@code password_hash} 不覆盖</b>（防重置在职人员口令）；</li>
 *   <li><b>工号</b>：{@code employee_no} 必填 + 文件内唯一 + 库内唯一（水印 REQ-USER-004 / AC-44）；</li>
 *   <li><b>手机号</b>：写入前经 {@link PhoneCryptoService#encryptForStore} 加密（阶段 1.7）；
 *       校验的仍是明文格式（{@code E-USER-003} 按 11 位大陆手机号）；</li>
 *   <li><b>离职补偿控制</b>（§8.1 / AC-12）：{@code status=离职} 且名下有未处理待办 → {@code E-USER-009} 拒绝整批；</li>
 *   <li><b>数据域</b>：{@code company_path}/{@code dept_path} 指向的组织必须在导入人数据域内（{@code E-USER-020}）；</li>
 *   <li><b>初始口令</b>：仅新增时生成（随机 + 线下分发 + 首登强制改密，T-03），不回写库、不进审计日志。</li>
 * </ol>
 */
@Component
public class UserImportStrategy implements ImportStrategy {

    private static final Pattern ACCOUNT = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{7,63}$");

    private static final Pattern EMPLOYEE_NO = Pattern.compile("^[A-Za-z0-9-]{1,32}$");

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private final SysUserMapper userMapper;

    private final InFlightChecker inFlightChecker;

    private final PasswordService passwordService;

    private final PhoneCryptoService phoneCrypto;

    public UserImportStrategy(SysUserMapper userMapper, InFlightChecker inFlightChecker,
                              PasswordService passwordService, PhoneCryptoService phoneCrypto) {
        this.userMapper = userMapper;
        this.inFlightChecker = inFlightChecker;
        this.passwordService = passwordService;
        this.phoneCrypto = phoneCrypto;
    }

    @Override
    public ImportKind kind() {
        return ImportKind.USER;
    }

    @Override
    public void write(Object parsed, ImportContext context, ImportReport report) {
        commit(cast(parsed), context, report);
    }

    @SuppressWarnings("unchecked")
    private static List<UserImportRow> cast(Object parsed) {
        return (List<UserImportRow>) parsed;
    }

    @Override
    public Object validate(CsvTable table, ImportContext context, ImportReport report) {
        List<UserImportRow> rows = new ArrayList<>();
        Set<String> seenAccounts = new HashSet<>();
        Set<String> seenEmployeeNos = new HashSet<>();
        for (CsvTable.Row row : table.rows()) {
            UserImportRow parsed = new UserImportRow(row);
            rows.add(parsed);
            parsed.account = parsed.account.toLowerCase(Locale.ROOT);
            if (!ACCOUNT.matcher(parsed.account).matches()) {
                error(report, row, "account", parsed.account,
                        "account 必填、8-64 位、字母开头，仅含字母/数字/下划线/点/连字符", "E-USER-001");
                parsed.invalid = true;
            } else if (!seenAccounts.add(parsed.account)) {
                error(report, row, "account", parsed.account, "账号在文件内重复，请合并或改名", "E-USER-002");
                parsed.invalid = true;
            }
            if (parsed.employeeNo.isEmpty() || !EMPLOYEE_NO.matcher(parsed.employeeNo).matches()) {
                error(report, row, "employee_no", parsed.employeeNo,
                        "employee_no 必填、1-32 字符、仅含字母/数字与连字符（水印使用）", "E-USER-014");
                parsed.invalid = true;
            } else if (!seenEmployeeNos.add(parsed.employeeNo)) {
                error(report, row, "employee_no", parsed.employeeNo, "工号在文件内重复，请核对人事花名册",
                        "E-USER-015");
                parsed.invalid = true;
            }
            if (parsed.phone.isEmpty() || !PHONE.matcher(parsed.phone).matches()) {
                error(report, row, "phone", parsed.phone,
                        "手机号格式不正确：请填写 11 位大陆手机号（^1[3-9]\\d{9}$）", "E-USER-003");
                parsed.invalid = true;
            }
            if (!parsed.email.isEmpty() && (parsed.email.length() > 128 || !EMAIL.matcher(parsed.email).matches())) {
                error(report, row, "email", parsed.email, "email 非空时格式必须合法且 <=128 字符", "E-USER-004");
                parsed.invalid = true;
            }
            if (parsed.name.isEmpty() || parsed.name.length() > 50) {
                error(report, row, "name", parsed.name, "name 必填且 <=50 字符", "E-USER-010");
                parsed.invalid = true;
            }
            if (parsed.remark.length() > 255) {
                error(report, row, "remark", parsed.remark, "remark <=255 字符", "E-USER-012");
                parsed.invalid = true;
            }
            UserStatus status = IdentityEnums.UserStatus.ofCode(parsed.status);
            if (status == null && "在职".equals(parsed.status)) {
                status = UserStatus.ACTIVE;
            }
            if (status == null && "离职".equals(parsed.status)) {
                status = UserStatus.RESIGNED;
            }
            if (status == null) {
                error(report, row, "status", parsed.status,
                        "status 只能是 在职 / 离职（停用由后台单条操作，import-spec T-05）", "E-USER-007");
                parsed.invalid = true;
            }
            parsed.userStatus = status;
        }

        Map<Long, String> nameIndex = OrgService.businessPathIndex(
                new ArrayList<>(context.snapshot().orgsById().values()));
        Map<String, SysOrg> byBusinessPath = new HashMap<>();
        for (SysOrg org : context.snapshot().orgsById().values()) {
            String businessPath = nameIndex.get(org.getId());
            if (businessPath != null) {
                byBusinessPath.put(businessPath, org);
            }
        }

        for (UserImportRow row : rows) {
            if (row.invalid) {
                continue;
            }
            CsvTable.Row source = row.source;
            if (row.companyPath.isEmpty()) {
                error(report, source, "company_path", row.companyPath,
                        "company_path 必填（集团本部人员填「集团」）", "E-USER-005");
                row.invalid = true;
                continue;
            }
            SysOrg company = byBusinessPath.get(row.companyPath);
            if (company == null) {
                error(report, source, "company_path", row.companyPath,
                        "company_path 在组织表中不存在（请先导入组织，import-spec §2.2 前置条件①）", "E-USER-005");
                row.invalid = true;
                continue;
            }
            OrgType companyType = IdentityEnums.OrgType.ofCode(company.getOrgType());
            if (companyType != OrgType.COMPANY && companyType != OrgType.GROUP) {
                error(report, source, "company_path", row.companyPath,
                        "company_path 只能指向「公司」节点（集团本部人员填「集团」）", "E-USER-008");
                row.invalid = true;
                continue;
            }
            SysOrg dept = null;
            if (!row.deptPath.isEmpty()) {
                dept = byBusinessPath.get(row.deptPath);
                if (dept == null) {
                    error(report, source, "dept_path", row.deptPath, "dept_path 在组织表中不存在（请先导入组织）",
                            "E-USER-006");
                    row.invalid = true;
                    continue;
                }
                OrgType deptType = IdentityEnums.OrgType.ofCode(dept.getOrgType());
                if (deptType != OrgType.DEPT && deptType != OrgType.SECTION) {
                    error(report, source, "dept_path", row.deptPath, "dept_path 只能是部门或科室", "E-USER-006");
                    row.invalid = true;
                    continue;
                }
                if (!OrgHierarchy.isDescendantOrSelf(dept.getPath(), company.getPath())) {
                    error(report, source, "dept_path", row.deptPath,
                            "dept_path 必须位于 company_path 子树内", "E-USER-011");
                    row.invalid = true;
                    continue;
                }
            }
            if (!context.scope().require(kind(), company.getPath(), source.line(), "company_path",
                    row.companyPath, report.getFindings())) {
                row.invalid = true;
                continue;
            }
            if (dept != null && !context.scope().require(kind(), dept.getPath(), source.line(), "dept_path",
                    row.deptPath, report.getFindings())) {
                row.invalid = true;
                continue;
            }
            row.company = company;
            row.dept = dept;
            SysUser existing = context.snapshot().userByAccount(row.account);
            row.existing = existing;
            if (existing != null && !java.util.Objects.equals(existing.getName(), row.name)) {
                error(report, source, "name", row.name,
                        "账号已存在但姓名不一致（库中为「" + existing.getName()
                                + "」），防止误合并到他人账号；请核对人事花名册", "E-USER-013");
                row.invalid = true;
                continue;
            }
            Long excludeId = existing == null ? null : existing.getId();
            if (userMapper.countByEmployeeNo(row.employeeNo, excludeId) > 0) {
                error(report, source, "employee_no", row.employeeNo,
                        "工号在库中已存在（需全库唯一，import-spec E-USER-015）", "E-USER-015");
                row.invalid = true;
                continue;
            }
            if (row.userStatus == UserStatus.RESIGNED && existing != null) {
                InFlightChecker.InFlightSummary summary = inFlightChecker.checkUser(existing.getId());
                if (summary.pendingTasks() > 0) {
                    error(report, source, "status", row.status,
                            "离职前必须清空名下待办：" + row.name + "（" + row.account + "）名下仍有 "
                                    + summary.pendingTasks() + " 条未处理待办，请先转办或由系统管理员改派"
                                    + (summary.bizNos().isEmpty() ? ""
                                            : "；涉及单号：" + String.join("、", summary.bizNos())),
                            "E-USER-009");
                    row.invalid = true;
                }
            }
        }
        return rows;
    }

    private void commit(List<UserImportRow> rows, ImportContext context, ImportReport report) {
        int added = 0;
        int updated = 0;
        int skipped = 0;
        for (UserImportRow row : rows) {
            String encryptedPhone = phoneCrypto.encryptForStore(row.phone);
            if (row.existing == null) {
                SysUser user = new SysUser();
                user.setAccount(row.account);
                user.setName(row.name);
                user.setEmployeeNo(row.employeeNo);
                String initialPassword = InitialPasswordGenerator.generate(passwordService, 12);
                user.setPasswordHash(passwordService.encode(initialPassword));
                user.setPhone(encryptedPhone);
                user.setEmail(blankToNull(row.email));
                user.setCompanyId(row.company.getId());
                user.setOrgId(row.dept == null ? null : row.dept.getId());
                user.setStatus(row.userStatus.code());
                user.setRemark(blankToNull(row.remark));
                user.setCreatedBy(context.operatorId());
                user.setUpdatedBy(context.operatorId());
                userMapper.insertUser(user);
                report.credential(row.account, row.name, initialPassword);
                added++;
            } else {
                boolean changed = !java.util.Objects.equals(row.existing.getEmployeeNo(), row.employeeNo)
                        || !java.util.Objects.equals(row.existing.getName(), row.name)
                        || !java.util.Objects.equals(row.existing.getEmail(), blankToNull(row.email))
                        || !java.util.Objects.equals(row.existing.getCompanyId(), row.company.getId())
                        || !java.util.Objects.equals(row.existing.getOrgId(),
                                row.dept == null ? null : row.dept.getId())
                        || !java.util.Objects.equals(row.existing.getStatus(), row.userStatus.code())
                        || !java.util.Objects.equals(blankToNull(row.existing.getRemark()), blankToNull(row.remark))
                        || phoneChanged(row.existing.getPhone(), row.phone);
                if (!changed) {
                    skipped++;
                    continue;
                }
                SysUser user = new SysUser();
                user.setId(row.existing.getId());
                user.setEmployeeNo(row.employeeNo);
                user.setName(row.name);
                user.setPhone(encryptedPhone);
                user.setEmail(blankToNull(row.email));
                user.setOrgId(row.dept == null ? null : row.dept.getId());
                user.setCompanyId(row.company.getId());
                user.setStatus(row.userStatus.code());
                user.setRemark(blankToNull(row.remark));
                user.setUpdatedBy(context.operatorId());
                userMapper.updateUserImport(user);
                updated++;
            }
        }
        report.stats(added, updated, skipped);
        if (added > 0) {
            report.note("新增人员已生成随机初始口令：仅本次响应返回，请通过线下加密清单分发，"
                    + "首次登录强制改密（import-spec T-03）；口令不进审计日志");
        }
        report.note("人员导入按 account 业务键 upsert，password_hash 不被覆盖；手机号写入前已 AES-256-GCM 加密");
    }

    /** 手机号是否真的变化（库中是密文：同明文每次加密都不同，必须解密后比较）。 */
    private boolean phoneChanged(String stored, String plain) {
        if (stored == null || stored.isBlank()) {
            return true;
        }
        try {
            return !plain.equals(phoneCrypto.decryptForRead(stored));
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private void error(ImportReport report, CsvTable.Row row, String column, String value, String message,
                       String code) {
        String number = code.substring(code.lastIndexOf('-') + 1);
        report.add(ImportFinding.error(kind().error(number), kind().fileName(), row.line(), column, value, message));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 一行人员数据。 */
    static final class UserImportRow {

        final CsvTable.Row source;

        String account;

        final String employeeNo;

        final String name;

        final String phone;

        final String email;

        final String companyPath;

        final String deptPath;

        final String status;

        final String remark;

        boolean invalid;

        UserStatus userStatus;

        SysOrg company;

        SysOrg dept;

        SysUser existing;

        UserImportRow(CsvTable.Row source) {
            this.source = source;
            this.account = source.get("account");
            this.employeeNo = source.get("employee_no");
            this.name = source.get("name");
            this.phone = source.get("phone");
            this.email = source.get("email");
            this.companyPath = source.get("company_path");
            this.deptPath = source.get("dept_path");
            this.status = source.get("status");
            this.remark = source.get("remark");
        }
    }
}
