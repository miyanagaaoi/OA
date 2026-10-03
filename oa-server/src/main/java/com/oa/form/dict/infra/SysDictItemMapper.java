package com.oa.form.dict.infra;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 数据字典 Mapper（{@code sys_dict_item}，doc/data-model.md §3.6）。
 *
 * <h2>为什么**不需要** {@code @dataScope} 标记</h2>
 * <p>{@code sys_dict_item} 是**配置数据**，未登记在 {@code oa.scope.tables}
 * （受控表只有 {@code flow_instance} / {@code form_data} / {@code flow_task} / {@code flow_routing} /
 * {@code sys_user}，实体注解再加 {@code sys_org} / {@code sys_org_leader} / {@code sys_user_position}）。
 * 织入数据域片段会按调用人的**单据可见范围**裁剪字典 → 下拉框在域外直接空掉（配置面崩坏），
 * 与 {@code FlowTemplateMapper} 的「配置数据不织入」口径逐字一致。
 *
 * <p>访问控制由入口权限（{@code flow}）承担：字典是**只读参考数据**，不含任何单据事实。
 *
 * <p><b>不继承 {@code BaseMapper}</b>：与全部受控表 Mapper 同一条纪律
 * （{@code DataScopeMapperGuardTest} 会静态断言本接口不在 {@code BaseMapper} 家族内）。
 */
@Mapper
public interface SysDictItemMapper {

    /** 某字典类型的**启用项**（下拉取值口径；按 {@code sort_no, id} 排序）。 */
    List<SysDictItemRow> selectEnabledByType(@Param("dictType") String dictType);

    /** 某字典类型的**全部项**（含停用；用于取值合法性判定与快照名称解析）。 */
    List<SysDictItemRow> selectAllByType(@Param("dictType") String dictType);

    /** 全部字典类型的行数（自检/披露用：`dict_type → 行数`）。 */
    List<java.util.Map<String, Object>> countByType();
}
