package com.oa.workflow.runtime.infra.row;

import java.time.LocalDateTime;

/**
 * 抄送行（{@code flow_cc}，doc/data-model.md §6.3）。
 *
 * <p>唯一键 {@code (instance_id, user_id)}（重复抄送幂等）；
 * {@code source} ∈ {@code initiator}（发起人指定）/ {@code template}（模板固定）。
 * 抄送人**只读可见、不产生待办、不产生审批决议**（enums.md §8）。
 */
public class FlowCcRow {

    private Long id;
    private Long instanceId;
    private Long userId;
    private String source;
    private LocalDateTime readAt;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
