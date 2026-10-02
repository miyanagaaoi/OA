package com.oa.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 组织（{@code oa.identity.org.*}）接口出入参。
 *
 * <p>枚举口径：入参 {@code orgType}/{@code status} **同时接受 code 与中文标签**
 * （{@code group} 或 {@code 集团}），集中由 {@code IdentityEnums} 解析；
 * 出参**同时给出 code 与中文 label**（{@code orgType} + {@code orgTypeLabel}），
 * 便于前端直接展示且不丢机器可读值。
 *
 * <p>路径口径（与 {@code sys_org.path} 的 DDL 注释一致）：{@code path} 是 **id 路径**
 * （{@code /1/12/135/}，含自身）；{@code businessPath} 是 **名称路径**
 * （{@code 集团/公司A/部门1}），即 import-spec §1.3 的业务键 {@code org_path}，
 * 用于与导入/导出口径对齐（DDL 没有存名称路径的列，由服务层实时拼装）。
 */
public final class OrgDtos {

    private OrgDtos() {
    }

    /** 新增组织（{@code POST /api/v1/identity/orgs}）。 */
    public record OrgCreateRequest(
            @NotBlank(message = "组织名称不能为空") @Size(max = 100, message = "组织名称不得超过 100 字符") String name,
            @NotBlank(message = "组织类型不能为空") String orgType,
            Long parentId,
            Integer sortNo,
            String status,
            @Size(max = 255, message = "备注不得超过 255 字符") String remark
    ) {
    }

    /** 改名 / 排序 / 备注（{@code PUT /api/v1/identity/orgs/{id}}）。类型不可改，需改类型走 move 重建层级。 */
    public record OrgUpdateRequest(
            @NotBlank(message = "组织名称不能为空") @Size(max = 100, message = "组织名称不得超过 100 字符") String name,
            Integer sortNo,
            @Size(max = 255, message = "备注不得超过 255 字符") String remark
    ) {
    }

    /**
     * 换父级（{@code POST /api/v1/identity/orgs/{id}/move}）；{@code newParentId} 为空表示挂为根（仅集团）。
     *
     * <p>{@code reason}/{@code force} 为**危险操作留痕字段**（AC-52）：{@code force=true} 时必须非空
     * {@code reason}（否则 400），且调用人必须是系统管理员（否则 403）；
     * 两个值都会随 {@code @Audited(recordBefore=true, recordArgs=true)} 写进 {@code sys_log} 的入参 JSON。
     *
     * <p><b>注意</b>：{@code force} 只影响「准入校验 + 留痕」，**不改变 {@code oa.identity.block-on-inflight}
     * 的拦截判定**——该开关是 AC-11/AC-12 的硬阻断口径，见 {@code orgs/{id}/disable}。
     */
    public record OrgMoveRequest(
            Long newParentId,
            @Size(max = 500, message = "原因不得超过 500 字符") String reason,
            Boolean force
    ) {
    }

    /**
     * 危险操作的可选请求体（停用/启用组织、解除负责人绑定）—— {@code {reason?, force?}}。
     *
     * <p>AC-52：「管理员改派/强制操作必须填写原因，轨迹与审计日志均留痕」。
     * 本请求体是**可选**的（{@code @RequestBody(required = false)}），旧调用不带 body 仍可正常使用；
     * 一旦带了 {@code force=true}，则：{@code reason} 必须非空（否则 400）、调用人必须是系统管理员（否则 403）。
     * {@code reason}/{@code force} 由 {@code @Audited} 的入参 JSON 落到 {@code sys_log}。
     */
    public record StateChangeRequest(
            @Size(max = 500, message = "原因不得超过 500 字符") String reason,
            Boolean force
    ) {
    }

    /**
     * 组织视图（树接口的 {@code children} 非空；平铺接口为 {@code null}）。
     *
     * <p>{@code leaderId} 是 {@code sys_org.leader_id} 的**冗余**值（权威数据在
     * {@code sys_org_leader}）；负责人明细请走 {@code GET /orgs/{id}/leaders}，本视图不展开姓名
     * （避免树接口 N+1 查询）。
     *
     * <p>{@code hasPrimaryLeader}（AC-11「未设正职」标示）：来自 {@code sys_org_leader} 中
     * {@code leader_type='primary' AND category IS NULL} 是否存在。与 {@code leaderId} 的区别：
     * 前者是**权威表**的判定（业务线分管领导不算正职），后者只是 {@code sys_org} 的冗余列；
     * 全树由**一次批量查询**聚合（{@code SysOrgLeaderMapper#selectPrimaryOrgIds}），不做逐节点查询。
     */
    public record OrgView(
            Long id,
            Long parentId,
            String orgType,
            String orgTypeLabel,
            String name,
            String path,
            String businessPath,
            Integer depth,
            Long leaderId,
            Integer sortNo,
            String status,
            String statusLabel,
            String remark,
            List<OrgView> children,
            boolean hasPrimaryLeader
    ) {
    }

    /** 下拉/级联选项（精简树，{@code value/label} 便于前端直接绑定）。 */
    public record OrgOption(
            Long value,
            String label,
            String orgType,
            String orgTypeLabel,
            String path,
            Integer depth,
            boolean disabled,
            List<OrgOption> children
    ) {
    }

    /** 路径与层级（{@code GET /orgs/{id}/path}）。 */
    public record OrgPathView(
            Long id,
            String path,
            String businessPath,
            Integer depth,
            List<OrgPathSegment> segments
    ) {
    }

    /** 路径上的单个节点（根 → 自身）。 */
    public record OrgPathSegment(
            Long id,
            String name,
            String orgType,
            String orgTypeLabel
    ) {
    }

    /** move 结果（含级联重算明细，便于前端/审计核对）。 */
    public record MoveResult(
            Long id,
            Long newParentId,
            String oldPath,
            String newPath,
            Integer newDepth,
            int movedNodes,
            List<MoveItem> items
    ) {
    }

    /** move 级联重算的单节点明细。 */
    public record MoveItem(
            Long id,
            String oldPath,
            String newPath,
            Integer depth
    ) {
    }

    /**
     * 在途/待办检查结果（同时也是 disable / resign 的「影响清单」载荷）。
     *
     * <p><b>向后兼容</b>：{@code inFlightInstances}/{@code pendingTasks}/{@code total}/{@code bizNos}
     * 是前端已在使用的既有字段名，**一律保留**；本次只**追加**
     * {@code inFlightInstanceCount}/{@code pendingTaskCount}（同值的规范名）、
     * {@code activeStaffCount}（该节点子树下的在职人数，W-ORG-017）与 {@code items}（明细行）。
     *
     * @param inFlightInstanceCount 在途单据数（规范名，与 {@code inFlightInstances} 同值）
     * @param pendingTaskCount      待处理待办数（规范名，与 {@code pendingTasks} 同值）
     * @param activeStaffCount      该节点**子树**下的在职人员数（组织侧语义；人员侧为 0）
     * @param items                 影响清单明细（无在途/待办时为空数组，绝不返回 {@code null}）
     */
    public record InFlightCheckView(
            boolean blocked,
            boolean rejected,
            boolean blockOnInflight,
            int inFlightInstances,
            int pendingTasks,
            int total,
            List<String> bizNos,
            String message,
            int inFlightInstanceCount,
            int pendingTaskCount,
            int activeStaffCount,
            List<InFlightDtos.InFlightItemView> items
    ) {
    }

    /** 启停结果。 */
    public record StateResult(
            Long id,
            String status,
            String statusLabel,
            List<String> warnings,
            InFlightCheckView impact
    ) {
    }
}
