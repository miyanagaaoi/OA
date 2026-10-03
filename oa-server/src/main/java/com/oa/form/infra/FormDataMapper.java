package com.oa.form.infra;

import com.oa.form.infra.row.FormDataFullRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 表单数据 Mapper（{@code form_data}，doc/data-model.md §4.3）—— 2b 的读写全列入口。
 *
 * <h2>数据域纪律（fail-closed）</h2>
 * <ul>
 *   <li>{@code form_data} 是**受控表**（{@code oa.scope.tables}，{@code kind=INSTANCE}）：
 *       每条 SELECT **恰好 1 个** {@code @dataScope} 标记，域外查不到（按 404 处理）；</li>
 *   <li>{@code countApprovedInstanceByBizNo} 是「关联合同单号必须存在且已通过」的**全局唯一性判定**
 *       （{@code doc/forms.md} §3 {@code contract_ref} 行）：唯一性是全局约束、与调用人数据域无关，
 *       因此语句**保留**标记、由调用方在 {@code DataScopeContext.system()} 下执行 ——
 *       与 {@code FlowInstanceMapper#countByBizNo} 的做法逐字一致（**不新增豁免条目**）；</li>
 *   <li><b>不继承 {@code BaseMapper}</b>（MP 注入语句无标记，会被 40303 拒绝）。</li>
 * </ul>
 */
@Mapper
public interface FormDataMapper {

    /** 按 id 取表单数据行（数据域过滤：域外返回 {@code null}）。 */
    FormDataFullRow selectFormDataById(@Param("id") Long id);

    /** 按单号取表单数据行（数据域过滤）。 */
    FormDataFullRow selectFormDataByBizNo(@Param("bizNo") String bizNo);

    /**
     * 取收款账号密文（数据域过滤）。
     *
     * <p>密文是 **ASCII 形态**（{@code v1:&lt;keyId&gt;:&lt;base64(iv+ct+tag)&gt;}，见
     * {@code PhoneCipher}），因此按 {@code String} 读写：声明为 {@code byte[]} 时
     * MyBatis 会按 {@code Byte[]}（包装类型数组）取值，赋值给 {@code byte[]} 会抛
     * {@code IllegalArgumentException: argument type mismatch}（2026-10-03 运行期实测）。
     */
    String selectPayeeAccountCipher(@Param("id") Long id);

    /**
     * 已通过（{@code status='approved'}）的实例数（按单号）。
     *
     * <p>调用方必须包在 {@code DataScopeContext.system()} 内（全局唯一性口径）。
     */
    int countApprovedInstanceByBizNo(@Param("bizNo") String bizNo);

    // ------------------------------------------------------------------ 写

    /**
     * 固化字段值与表单快照版本（templates.md §3.1 的三层版本之「表单快照版本」）。
     *
     * @param schemaVersion 提交时的 {@code flow_template.version}（V-03 / V-08：
     *                      {@code schema_version} 必须等于对应 {@code flow_template.version}）
     */
    int updateFieldsJson(@Param("id") Long id,
                         @Param("fieldsJson") String fieldsJson,
                         @Param("schemaVersion") Integer schemaVersion);

    /** 写收款账号密文（该字段**永不进 {@code fields_json}**，见 data-model.md §8.2）。 */
    int updatePayeeAccountCipher(@Param("id") Long id, @Param("cipher") String cipher);

    /** 清空收款账号密文（把字段值从表单里移除时同步清理，避免留下孤儿密文）。 */
    int clearPayeeAccountCipher(@Param("id") Long id);
}
