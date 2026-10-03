package com.oa.workflow.runtime.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 运行时动作的入参（2a.4 / 2a.5）。
 *
 * <p>校验分两层：Bean Validation 只做「非空 / 长度」的机械校验，
 * **业务口径**（驳回意见 ≥5 字、原因必填、闸门剩余次数、节点开关）一律在引擎层
 * （{@code ApprovalOpinionPolicy} / {@code GateCounterPolicy} / {@code FlowEngineService}），
 * 避免出现「接口放行、引擎拒绝」两套口径。
 */
public final class RuntimeRequests {

    private RuntimeRequests() {
    }

    /** 通过（{@code opinion} 可选；{@code collabDeptIds} 仅 ② 生效）。 */
    public record ApproveRequest(
            @Size(max = 1000, message = "审批意见不得超过 1000 字") String opinion,
            /** ② 通过时勾选的协同部门（PRD §6.3 / §6.4；每个部门一组独立并行子任务）。 */
            List<Long> collabDeptIds
    ) {
    }

    /** 驳回（意见必须在引擎侧过 ≥5 字闸门，此处只限长度上限）。 */
    public record RejectRequest(
            @Size(max = 1000, message = "驳回意见不得超过 1000 字") String opinion
    ) {
    }

    /** 归档登记（⑦ 仅登记不审批；意见可选）。 */
    public record ArchiveRegisterRequest(
            @Size(max = 1000, message = "登记说明不得超过 1000 字") String opinion
    ) {
    }

    /** 需要填写原因的动作（回退 / 终止 / 撤回 / 回到本部门）。 */
    public record ReasonRequest(
            @NotBlank(message = "必须填写原因")
            @Size(max = 255, message = "原因不得超过 255 字") String reason
    ) {
    }

    /** 流转（指定承接部门 + 原因）。 */
    public record RouteRequest(
            @NotNull(message = "必须选择承接部门") Long toDeptId,
            @NotBlank(message = "必须填写流转原因")
            @Size(max = 255, message = "流转原因不得超过 255 字") String reason
    ) {
    }

    /** 自由跳转（目标节点序号 + 原因）。 */
    public record JumpRequest(
            @NotNull(message = "必须指定目标节点序号") Integer targetSeq,
            @NotBlank(message = "必须填写跳转原因")
            @Size(max = 255, message = "跳转原因不得超过 255 字") String reason
    ) {
    }

    /** 加签（前/后 + 加签人 + 原因）。 */
    public record AddSignRequest(
            @NotBlank(message = "必须指定加签类型（pre=前加签 / post=后加签）") String type,
            @NotNull(message = "必须指定加签人") Long delegateUserId,
            @NotBlank(message = "必须填写加签原因")
            @Size(max = 255, message = "加签原因不得超过 255 字") String reason
    ) {
    }

    /** 转办 / 改派（目标处理人 + 原因）。 */
    public record HandoverRequest(
            @NotNull(message = "必须指定目标处理人") Long toUserId,
            @NotBlank(message = "必须填写原因")
            @Size(max = 255, message = "原因不得超过 255 字") String reason
    ) {
    }

    /** 提交补件（备注可选；主字段只读）。 */
    public record SupplementSubmitRequest(
            @Size(max = 500, message = "补件说明不得超过 500 字") String note
    ) {
    }

    /** 抄送登记（发起人指定；只读可见，不产生待办）。 */
    public record CcRequest(
            @NotNull(message = "抄送人不能为空") List<Long> userIds
    ) {
    }
}
