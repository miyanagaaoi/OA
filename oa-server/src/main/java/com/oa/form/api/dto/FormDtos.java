package com.oa.form.api.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 表单域（2b）的入参 / 出参。
 *
 * <p>载荷一律用 {@code Map<String, Object>} 而不是强类型 DTO：字段集由
 * {@code flow_template.form_schema_json} **运行时**驱动（{@code doc/forms.md} §11.1
 * 「表单由 {@code form_schema_json} 驱动，不在前端硬编码字段」），
 * 服务端也不允许把字段清单编译进类结构 —— 那样「新增字段不走发版」就无从谈起。
 */
public final class FormDtos {

    private FormDtos() {
    }

    /**
     * 表单写入请求（保存 / 校验共用）。
     *
     * @param fields 字段值（键 = 字段 {@code code}；未登记的键**一律拒绝**）
     * @param mode   校验档：{@code draft}（默认，不强制无条件必填）/ {@code submit}（提交前口径）
     */
    public record FormWriteRequest(
            Map<String, Object> fields,
            @Size(max = 16, message = "mode 只能取 draft / submit") String mode
    ) {
    }

    /** 归还登记请求（印鉴单三态例外的专用通道）。 */
    public record SealReturnRequest(
            String returnStatus,
            String returnDate,
            @Size(max = 255, message = "说明不得超过 255 字符") String reason
    ) {
    }

    /** 字典项视图。 */
    public record DictItemView(
            String dictType,
            String itemCode,
            String itemName,
            String itemNameEn,
            Integer sortNo,
            String status
    ) {
    }

    /** 字典类型清单项。 */
    public record DictTypeView(String dictType, String label, int itemCount, String evidence) {
    }

    /** 事项单分支判定请求。 */
    public record InvolveCostRequest(Boolean involveCost, Map<String, Object> fields) {
    }

    /** 事项单类别改判判定请求。 */
    public record CategoryChangeRequest(String currentCategory, String newCategory, Long instanceId,
                                        String state) {
    }

    /** 校验结果视图（干跑接口的固定形状）。 */
    public record ValidationView(
            boolean passed,
            int issueCount,
            List<Map<String, Object>> issues
    ) {
    }
}
