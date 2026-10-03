package com.oa.workflow.runtime.infra.row;

import java.time.LocalDateTime;

/**
 * 补件请求行（{@code flow_supplement}，doc/data-model.md §5.5）。
 *
 * <p>{@code supplement_round} 在**请求补件时占位**（第 N 次请求即第 N 轮），
 * 提交补件不改轮次（§5.5 / §8.2）；{@code deadline} 由 Q7 的
 * {@code supplementDeadlineDays + supplementDeadlineType} 算出（见
 * {@code com.oa.workflow.runtime.domain.SupplementDeadlinePolicy}）。
 */
public class FlowSupplementRow {

    private Long id;
    private Long instanceId;
    private Long nodeInstanceId;
    private Long requestedBy;
    private String reason;
    private Integer supplementRound;
    private LocalDateTime deadline;
    private LocalDateTime submittedAt;
    private Long submittedBy;
    private String submittedNote;
    private String status;
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

    public Long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Long requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getSupplementRound() {
        return supplementRound;
    }

    public void setSupplementRound(Integer supplementRound) {
        this.supplementRound = supplementRound;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Long getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(Long submittedBy) {
        this.submittedBy = submittedBy;
    }

    public String getSubmittedNote() {
        return submittedNote;
    }

    public void setSubmittedNote(String submittedNote) {
        this.submittedNote = submittedNote;
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
}
