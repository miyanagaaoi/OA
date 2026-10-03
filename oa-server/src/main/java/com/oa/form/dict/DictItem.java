package com.oa.form.dict;

/**
 * 字典项视图（{@code sys_dict_item} 一行；doc/data-model.md §3.6）。
 *
 * <p>字段与列一一对应；{@link #enabled()} 由 {@code status = 'active'} 决定。
 * 本期字典**只读消费**：维护入口属阶段 4（{@code oa.admin.dict.*}），
 * 表单侧只做「读取启用项 + 缓存 + 取值校验 + 快照名称解析」。
 *
 * @param dictType   {@code sys_dict_item.dict_type}（必须属于 {@link DictType} 白名单）
 * @param itemCode   {@code sys_dict_item.item_code}
 * @param itemName   中文名
 * @param itemNameEn 英文名（可空）
 * @param sortNo     排序号
 * @param status     启用态（{@code active} / 其他）
 * @param remark     备注（业务口径说明）
 */
public record DictItem(
        String dictType,
        String itemCode,
        String itemName,
        String itemNameEn,
        Integer sortNo,
        String status,
        String remark
) {

    /** 是否为启用项（只有启用项进下拉、才可通过取值校验）。 */
    public boolean enabled() {
        return status == null || "active".equalsIgnoreCase(status.trim());
    }
}
