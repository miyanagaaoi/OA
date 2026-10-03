package com.oa.form.infra.row;

import java.time.LocalDateTime;

/**
 * 表单数据完整行（{@code form_data}，doc/data-model.md §4.3）。
 *
 * <p>与 {@code com.oa.workflow.approver.infra.row.FormDataRow}（2a.3 的**最小建行模型**）的区别：
 * 后者只写 5 列；本类承担 2b 的**读写全列**（含
 * {@code payee_account_cipher}（敏感字段单独密文落列，**不进 fields_json**）与
 * {@code schema_version}（提交时固化的模板版本，templates.md §3.1 三层版本的中间层））。
 */
public class FormDataFullRow {

    private Long id;
    private String formType;
    private String bizNo;
    private String fieldsJson;
    private String payeeAccountCipher;
    private Integer schemaVersion;
    private Long creatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getFieldsJson() {
        return fieldsJson;
    }

    public void setFieldsJson(String fieldsJson) {
        this.fieldsJson = fieldsJson;
    }

    /** 收款账号密文（ASCII 形态 {@code v1:keyId:base64} 原样落 {@code VARBINARY(255)}；**永不进 fields_json**）。 */
    public String getPayeeAccountCipher() {
        return payeeAccountCipher;
    }

    public void setPayeeAccountCipher(String payeeAccountCipher) {
        this.payeeAccountCipher = payeeAccountCipher;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
