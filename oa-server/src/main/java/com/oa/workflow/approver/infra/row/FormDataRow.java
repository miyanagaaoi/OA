package com.oa.workflow.approver.infra.row;

/**
 * 表单数据行（{@code form_data}，doc/data-model.md §4.3）—— 仅用于**建实例时插入最小行**。
 *
 * <p>2a.3 只负责把实例挂到一个 {@code form_data} 上（{@code form_data_id} 是 NOT NULL 外键），
 * 真正的字段渲染、服务端二次校验与写入白名单属 2b（{@code oa.form.template.*}）。
 * 因此这里只写 {@code biz_no / form_type / fields_json / schema_version / creator_id} 五列，
 * {@code fields_json} 缺省写 {@code {}}（列 NOT NULL），{@code payee_account_cipher} 保持 {@code NULL}。
 */
public class FormDataRow {

    private Long id;
    private String bizNo;
    private String formType;
    private String fieldsJson;
    private Integer schemaVersion;
    private Long creatorId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public String getFieldsJson() {
        return fieldsJson;
    }

    public void setFieldsJson(String fieldsJson) {
        this.fieldsJson = fieldsJson;
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
}
