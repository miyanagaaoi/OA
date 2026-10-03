package com.oa.workflow.runtime.infra.row;

import java.time.LocalDateTime;

/**
 * 流转链行（{@code flow_routing}，doc/data-model.md §5.4）。
 *
 * <p>口径（§8.2）：
 * <ul>
 *   <li>{@code action_type} ∈ {@code route / rollback / back_home}；</li>
 *   <li>{@code seq} 从 1 递增，唯一键 {@code (instance_id, seq)}；</li>
 *   <li>{@code status = processing} 的 {@code rollback} 行表示「上一节点重审中」，
 *       上一节点通过后置 {@code finished}（PRD §7.2「上一节点通过后自动回到本节点」的唯一落点）；</li>
 *   <li>{@code back_home} **不计入** {@code flow_instance.routing_count}。</li>
 * </ul>
 */
public class FlowRoutingRow {

    private Long id;
    private Long instanceId;
    private Integer seq;
    private String actionType;
    private Long fromDeptId;
    private Long toDeptId;
    private Integer fromNodeSeq;
    private Integer toNodeSeq;
    private Long designatedBy;
    private String reason;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Long getFromDeptId() {
        return fromDeptId;
    }

    public void setFromDeptId(Long fromDeptId) {
        this.fromDeptId = fromDeptId;
    }

    public Long getToDeptId() {
        return toDeptId;
    }

    public void setToDeptId(Long toDeptId) {
        this.toDeptId = toDeptId;
    }

    public Integer getFromNodeSeq() {
        return fromNodeSeq;
    }

    public void setFromNodeSeq(Integer fromNodeSeq) {
        this.fromNodeSeq = fromNodeSeq;
    }

    public Integer getToNodeSeq() {
        return toNodeSeq;
    }

    public void setToNodeSeq(Integer toNodeSeq) {
        this.toNodeSeq = toNodeSeq;
    }

    public Long getDesignatedBy() {
        return designatedBy;
    }

    public void setDesignatedBy(Long designatedBy) {
        this.designatedBy = designatedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
