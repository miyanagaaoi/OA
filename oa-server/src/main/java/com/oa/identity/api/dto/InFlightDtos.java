package com.oa.identity.api.dto;

import java.util.List;

/**
 * 在途/待办**影响清单**出参（人员侧与组织侧共用同一行结构）。
 *
 * <p>口径来源：doc/import-spec.md §7.2「受影响在途单据清单」、§8.1（人员）/§8.2（组织）；
 * doc/prd-0.1.md §5.5、AC-11 / AC-12。
 *
 * <p>数据来源只有**一个端口** {@code com.oa.identity.app.InFlightChecker}
 * （施工要求第 3 条「复用同一端口，不要新造第二套检查逻辑」）：
 * 流程表未落地时缺省实现返回空清单，出参即空数组（不是 {@code null}）。
 *
 * <p>字段为空时 JSON 自动省略（{@code spring.jackson.default-property-inclusion=non_null}），
 * 因此组织侧一行只会出现 {@code bizNo/formType/initiatorName/nodeName} 四项，
 * 人员侧则含 {@code instanceId/currentNodeName/status}。
 */
public final class InFlightDtos {

    private InFlightDtos() {
    }

    /** 影响清单单行（在途实例或待办）。 */
    public record InFlightItemView(
            Long instanceId,
            String bizNo,
            String formType,
            String initiatorName,
            String nodeName,
            String currentNodeName,
            String status
    ) {
    }

    /**
     * 人员影响清单（{@code GET /api/v1/identity/users/{id}/in-flight-check}）。
     *
     * <p>比 {@code GET /users/{id}/pending-tasks} 更全：除待办数外，还给出在途单据数与明细行
     * （单据类型 / 发起人 / 当前节点 / 状态），用于离职、调岗、停用前的二次确认弹窗。
     *
     * @param pendingTaskCount      名下待处理待办数（{@code flow_task.status='pending'}）
     * @param inFlightInstanceCount 在途单据数（未完结态）
     * @param items                 明细清单（无在途/待办时为空数组）
     */
    public record UserInFlightCheckView(
            int pendingTaskCount,
            int inFlightInstanceCount,
            List<InFlightItemView> items
    ) {

        public UserInFlightCheckView {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
