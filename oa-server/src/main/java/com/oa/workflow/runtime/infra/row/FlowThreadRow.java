package com.oa.workflow.runtime.infra.row;

import java.time.LocalDateTime;

/**
 * 审批轨迹行（{@code sys_thread}，doc/data-model.md §6.5）。
 *
 * <p>{@code action} 取 doc/enums.md §9 的 **16 个定稿值**（{@code RuntimeEnums.ThreadAction}）；
 * {@code actor_name} / {@code actor_position} 是**快照**（防止改名后轨迹失真）。
 * 轨迹只追加、不可改、不可删（enums.md §9）。
 */
public class FlowThreadRow {

    private Long id;
    private Long instanceId;
    private Long nodeInstanceId;
    private Integer seq;
    private Long actorId;
    private String actorName;
    private String actorPosition;
    private String action;
    private String opinion;
    private Long signatureId;
    private LocalDateTime createdAt;

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

    public Long getNodeInstanceId() {
        return nodeInstanceId;
    }

    public void setNodeInstanceId(Long nodeInstanceId) {
        this.nodeInstanceId = nodeInstanceId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getActorPosition() {
        return actorPosition;
    }

    public void setActorPosition(String actorPosition) {
        this.actorPosition = actorPosition;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public Long getSignatureId() {
        return signatureId;
    }

    public void setSignatureId(Long signatureId) {
        this.signatureId = signatureId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
