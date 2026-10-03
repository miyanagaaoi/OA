package com.oa.form.dict.infra;

import com.oa.form.dict.DictItem;

/**
 * 字典项行（{@code sys_dict_item}，doc/data-model.md §3.6）。
 *
 * <p>MyBatis 落点：{@code mapper/form/FormDictMapper.xml}；列 → 属性按
 * {@code map-underscore-to-camel-case} 映射，故这里显式声明与列同名的属性。
 */
public class SysDictItemRow {

    private String dictType;
    private String itemCode;
    private String itemName;
    private String itemNameEn;
    private Integer sortNo;
    private String status;
    private String remark;

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getItemNameEn() {
        return itemNameEn;
    }

    public void setItemNameEn(String itemNameEn) {
        this.itemNameEn = itemNameEn;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    /** 行 → 值对象。 */
    public DictItem toItem() {
        return new DictItem(dictType, itemCode, itemName, itemNameEn, sortNo, status, remark);
    }
}
