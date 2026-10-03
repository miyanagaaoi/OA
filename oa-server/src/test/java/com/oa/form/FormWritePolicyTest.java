package com.oa.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.form.app.FormWritePolicy;
import com.oa.form.app.FormWritePolicy.FormState;
import com.oa.form.app.FormWritePolicy.FormType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 三态白名单服务端强制（doc/tech-design.md §5.4）纯逻辑单测。
 *
 * <p>草稿全可写 / 审批中全只读 / 待补件仅 attachments + supplement_note /
 * 唯一例外：印鉴单 return_status、return_date 仅发起人与节点⑦可改。
 */
class FormWritePolicyTest {

    private static final Set<String> MATTER_FIELDS =
            new LinkedHashSet<>(Set.of("title", "category", "involve_cost", "amount", "attachments", "supplement_note"));

    private static final Set<String> SEAL_FIELDS =
            new LinkedHashSet<>(Set.of("title", "seal_type", "attachments", "supplement_note", "return_status", "return_date"));

    @Test
    @DisplayName("草稿：全部字段可写")
    void draftAllWritable() {
        Set<String> writable = FormWritePolicy.writableFields(FormState.DRAFT, FormType.SEAL, true, false, SEAL_FIELDS);
        assertThat(writable).containsExactlyInAnyOrderElementsOf(SEAL_FIELDS);
    }

    @Test
    @DisplayName("审批中：全只读（非印鉴单）")
    void approvingReadOnly() {
        Set<String> writable = FormWritePolicy.writableFields(FormState.APPROVING, FormType.MATTER, true, false, MATTER_FIELDS);
        assertThat(writable).isEmpty();
        assertThatThrownBy(() -> FormWritePolicy.assertWritable("title", FormState.APPROVING, FormType.MATTER,
                true, false, MATTER_FIELDS))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED);
    }

    @Test
    @DisplayName("待补件：仅 attachments + supplement_note")
    void pendingSupplementOnlyTwoFields() {
        Set<String> writable = FormWritePolicy.writableFields(FormState.PENDING_SUPPLEMENT, FormType.MATTER, true, false, MATTER_FIELDS);
        assertThat(writable).containsExactlyInAnyOrder(FormWritePolicy.FIELD_ATTACHMENTS, FormWritePolicy.FIELD_SUPPLEMENT_NOTE);

        assertThatCode(() -> FormWritePolicy.assertWritable(Set.of("attachments", "supplement_note"),
                FormState.PENDING_SUPPLEMENT, FormType.MATTER, true, false, MATTER_FIELDS))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> FormWritePolicy.assertWritable(Set.of("attachments", "amount"),
                FormState.PENDING_SUPPLEMENT, FormType.MATTER, true, false, MATTER_FIELDS))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("唯一例外：印鉴单 return_status/return_date 仅在「审批中」态对发起人与节点⑦可写")
    void sealReturnFieldsException() {
        // 发起人（审批中）
        assertThat(FormWritePolicy.writableFields(FormState.APPROVING, FormType.SEAL, true, false, SEAL_FIELDS))
                .containsExactlyInAnyOrder(FormWritePolicy.FIELD_RETURN_STATUS, FormWritePolicy.FIELD_RETURN_DATE);
        // 节点⑦（归档登记，审批中）
        assertThat(FormWritePolicy.writableFields(FormState.APPROVING, FormType.SEAL, false, true, SEAL_FIELDS))
                .containsExactlyInAnyOrder(FormWritePolicy.FIELD_RETURN_STATUS, FormWritePolicy.FIELD_RETURN_DATE);
        // 非发起人且非节点⑦ → 无权
        assertThat(FormWritePolicy.writableFields(FormState.APPROVING, FormType.SEAL, false, false, SEAL_FIELDS)).isEmpty();
    }

    @Test
    @DisplayName("例外边界（2026-10-04 修正）：待补件期归还字段**同样只读**（TC-FORM-013）")
    void sealReturnFieldsAreReadOnlyWhileAwaitingSupplement() {
        // forms.md §5 例外边界表：「待补件 | 发起人 | 仅 attachments + supplement_note | 一律只读
        // —— return_status / return_date 在待补件期同样只读」；§7 对照表同口径；
        // 可执行用例 doc/test-cases.md TC-FORM-013。改前这里错误地放行了两个归还字段。
        assertThat(FormWritePolicy.writableFields(FormState.PENDING_SUPPLEMENT, FormType.SEAL, true, true, SEAL_FIELDS))
                .as("待补件期可写字段**只有** attachments + supplement_note")
                .containsExactlyInAnyOrder(FormWritePolicy.FIELD_ATTACHMENTS, FormWritePolicy.FIELD_SUPPLEMENT_NOTE);

        assertThatThrownBy(() -> FormWritePolicy.assertWritable("return_status",
                FormState.PENDING_SUPPLEMENT, FormType.SEAL, true, true, SEAL_FIELDS))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED);
    }

    @Test
    @DisplayName("例外不外溢：非印鉴单在审批中不可改 return_status")
    void exceptionDoesNotLeakToOtherFormTypes() {
        Set<String> fundFields = new LinkedHashSet<>(Set.of("title", "amount", "return_status", "attachments"));
        assertThat(FormWritePolicy.writableFields(FormState.APPROVING, FormType.FUND, true, true, fundFields)).isEmpty();
    }

    @Test
    @DisplayName("已完结/归档：一律只读（含印鉴单，无例外）")
    void closedIsReadOnly() {
        assertThat(FormWritePolicy.writableFields(FormState.CLOSED, FormType.SEAL, true, true, SEAL_FIELDS)).isEmpty();
        assertThat(FormWritePolicy.writableFields(FormState.CLOSED, FormType.MATTER, true, false, MATTER_FIELDS)).isEmpty();
    }

    @Test
    @DisplayName("状态映射：draft / approving / pending_supplement / 终态")
    void stateResolution() {
        assertThat(FormWritePolicy.resolveState("draft", null)).isEqualTo(FormState.DRAFT);
        assertThat(FormWritePolicy.resolveState("approving", null)).isEqualTo(FormState.APPROVING);
        assertThat(FormWritePolicy.resolveState("approving", "pending_supplement")).isEqualTo(FormState.PENDING_SUPPLEMENT);
        assertThat(FormWritePolicy.resolveState("approved", null)).isEqualTo(FormState.CLOSED);
        assertThat(FormWritePolicy.resolveState("rejected", null)).isEqualTo(FormState.CLOSED);
        assertThat(FormWritePolicy.resolveState("terminated", null)).isEqualTo(FormState.CLOSED);
    }

    @Test
    @DisplayName("filterWritable：静默剥离越权字段，保留可写字段")
    void filterPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attachments", "<file>");
        payload.put("supplement_note", "已补充发票");
        payload.put("amount", "1250000.00");
        payload.put("title", "篡改标题");

        Map<String, Object> filtered = FormWritePolicy.filterWritable(payload, FormState.PENDING_SUPPLEMENT,
                FormType.MATTER, true, false, MATTER_FIELDS);

        assertThat(filtered).containsOnlyKeys("attachments", "supplement_note");
        assertThat(FormWritePolicy.filterWritable(null, FormState.DRAFT, FormType.MATTER, true, false, MATTER_FIELDS))
                .isEmpty();
    }

    @Test
    @DisplayName("类型解析容错：未知/空 form_type 按事项单处理且不抛异常")
    void formTypeResolution() {
        assertThat(FormWritePolicy.FormType.of("seal")).isEqualTo(FormType.SEAL);
        assertThat(FormWritePolicy.FormType.of("  FUND ")).isEqualTo(FormType.FUND);
        assertThat(FormWritePolicy.FormType.of(null)).isEqualTo(FormType.MATTER);
        assertThat(FormWritePolicy.FormType.of("unknown")).isEqualTo(FormType.MATTER);
    }
}
