package com.oa.workflow.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.runtime.domain.RuntimeEnums.AddSignType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 加签链单测（2a.5）：前/后加签的相位机 + 准入校验 + **不触碰快照**的证据。
 *
 * <p>权威口径：doc/prd-0.1.md §6.4 REQ-FLOW-003（前加签：加签人先审，审完回到本人；
 * 后加签：本人审完加签人再审；加签人必须签署意见）、doc/data-model.md §5.2/§5.3。
 */
class AddSignPolicyTest {

    @Test
    @DisplayName("前加签：追加 awaiting_delegate 记录；链未闭环 → 引擎推迟节点决议")
    void preAddSign() {
        String chain = AddSignPolicy.append(null, AddSignType.PRE, 201L, 999L, null,
                AddSignPolicy.PHASE_AWAITING_DELEGATE, "金额较大，请财务复核");
        List<AddSignPolicy.Entry> entries = AddSignPolicy.read(chain);
        assertThat(entries).hasSize(1);
        AddSignPolicy.Entry entry = entries.get(0);
        assertThat(entry.order()).isEqualTo(1);
        assertThat(entry.type()).isEqualTo("pre");
        assertThat(entry.from()).isEqualTo(201L);
        assertThat(entry.to()).isEqualTo(999L);
        assertThat(entry.phase()).isEqualTo(AddSignPolicy.PHASE_AWAITING_DELEGATE);
        assertThat(entry.reason()).contains("请财务复核");
        assertThat(entry.createdAt()).isNotBlank();

        assertThat(AddSignPolicy.hasOpen(chain)).isTrue();
        assertThat(AddSignPolicy.openEntry(chain)).isPresent();
    }

    @Test
    @DisplayName("后加签：本人已同意（awaiting_owner），加签人闭环后 hasOpen=false")
    void postAddSignAndClose() {
        String chain = AddSignPolicy.append(null, AddSignType.POST, 201L, 999L, 55L,
                AddSignPolicy.PHASE_AWAITING_OWNER, "请分管领导再确认");
        assertThat(AddSignPolicy.hasOpen(chain)).isTrue();
        assertThat(AddSignPolicy.read(chain).get(0).delegateTaskId()).isEqualTo(55L);

        String closed = AddSignPolicy.close(chain, 55L);
        assertThat(AddSignPolicy.hasOpen(closed)).isFalse();
        assertThat(AddSignPolicy.read(closed).get(0).phase()).isEqualTo(AddSignPolicy.PHASE_DONE);
    }

    @Test
    @DisplayName("多条链：order 递增，close 只关闭指定任务；openEntry 取最后一条未闭环")
    void multipleEntries() {
        String chain = AddSignPolicy.append(null, AddSignType.PRE, 201L, 999L, 11L,
                AddSignPolicy.PHASE_AWAITING_DELEGATE, "第一次加签");
        chain = AddSignPolicy.close(chain, 11L);
        chain = AddSignPolicy.append(chain, AddSignType.POST, 201L, 888L, 22L,
                AddSignPolicy.PHASE_AWAITING_OWNER, "第二次加签");

        List<AddSignPolicy.Entry> entries = AddSignPolicy.read(chain);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).order()).isEqualTo(1);
        assertThat(entries.get(1).order()).isEqualTo(2);
        assertThat(entries.get(0).phase()).isEqualTo(AddSignPolicy.PHASE_DONE);
        assertThat(AddSignPolicy.openEntry(chain)).get()
                .extracting(AddSignPolicy.Entry::to).isEqualTo(888L);
    }

    @Test
    @DisplayName("bindTask：任务生成后回填 delegate_task_id（链与任务的唯一关联）")
    void bindTask() {
        String chain = AddSignPolicy.append(null, AddSignType.PRE, 201L, 999L, null,
                AddSignPolicy.PHASE_AWAITING_DELEGATE, "先加签");
        assertThat(AddSignPolicy.read(chain).get(0).delegateTaskId()).isNull();
        String bound = AddSignPolicy.bindTask(chain, 1, 321L);
        assertThat(AddSignPolicy.read(bound).get(0).delegateTaskId()).isEqualTo(321L);
        assertThat(AddSignPolicy.hasOpen(bound)).isTrue();
        assertThat(AddSignPolicy.hasOpen(AddSignPolicy.close(bound, 321L))).isFalse();
    }

    @Test
    @DisplayName("链 JSON 是 {\"chain\":[...]} 形态（snake_case 键），旧裸数组可被自动包装")
    void jsonShapeAndLegacyArray() {
        String chain = AddSignPolicy.append(null, AddSignType.PRE, 1L, 2L, null,
                AddSignPolicy.PHASE_AWAITING_DELEGATE, "r");
        assertThat(chain).startsWith("{\"chain\":[").contains("\"order\":1").contains("\"delegate_task_id\":null");

        String legacy = "[{\"order\":1,\"type\":\"pre\",\"from\":1,\"to\":2,\"phase\":\"awaiting_delegate\"}]";
        assertThat(AddSignPolicy.read(legacy)).hasSize(1);
        String upgraded = AddSignPolicy.append(legacy, AddSignType.POST, 1L, 3L, null,
                AddSignPolicy.PHASE_AWAITING_OWNER, "r2");
        assertThat(AddSignPolicy.read(upgraded)).hasSize(2);
    }

    @Test
    @DisplayName("链损坏不阻断审批：非法 JSON / null / 空白 → 空链")
    void brokenChainIsTolerated() {
        assertThat(AddSignPolicy.read(null)).isEmpty();
        assertThat(AddSignPolicy.read("   ")).isEmpty();
        assertThat(AddSignPolicy.read("{not-json")).isEmpty();
        assertThat(AddSignPolicy.read("{\"chain\":\"oops\"}")).isEmpty();
        assertThat(AddSignPolicy.hasOpen("{not-json")).isFalse();
        assertThat(AddSignPolicy.describe(null)).contains("无加签记录");
    }

    @Test
    @DisplayName("准入：节点未开加签 → 40910；加签人为自己 → 400；未指定 → 400")
    void assertAllowed() {
        assertThatThrownBy(() -> AddSignPolicy.assertAllowed(false, 201L, 999L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FLOW_ACTION_NOT_ALLOWED));
        assertThatThrownBy(() -> AddSignPolicy.assertAllowed(true, 201L, 201L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能给自己加签");
        assertThatThrownBy(() -> AddSignPolicy.assertAllowed(true, 201L, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("必须指定加签人");
        // 正常：无异常
        AddSignPolicy.assertAllowed(true, 201L, 999L);
    }

    @Test
    @DisplayName("加签类型解析：pre/post（大小写容错），其它值 400")
    void parseType() {
        assertThat(AddSignPolicy.parseType("pre")).isEqualTo(AddSignType.PRE);
        assertThat(AddSignPolicy.parseType("POST")).isEqualTo(AddSignType.POST);
        assertThatThrownBy(() -> AddSignPolicy.parseType("middle"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只支持 pre");
    }

    @Test
    @DisplayName("链只落 flow_node_instance.add_sign_chain_json：策略不产生任何快照键（不破坏 2a.3 契约）")
    void doesNotTouchSnapshotContract() {
        String chain = AddSignPolicy.append(null, AddSignType.PRE, 1L, 2L, null,
                AddSignPolicy.PHASE_AWAITING_DELEGATE, "r");
        // 链 JSON 的顶层键只有 chain；快照的键（template_version/parsed_at/basis/nodes）不被引用
        assertThat(chain).contains("\"chain\"")
                .doesNotContain("template_version")
                .doesNotContain("parsed_at")
                .doesNotContain("\"basis\"")
                .doesNotContain("\"nodes\"");
        assertThat(AddSignPolicy.KEY_CHAIN).isEqualTo("chain");
    }
}
