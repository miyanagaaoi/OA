package com.oa.identity.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.scope.DataScopeContext;
import com.oa.identity.app.InFlightChecker;
import com.oa.identity.app.InFlightChecker.InFlightItem;
import com.oa.identity.app.InFlightChecker.InFlightSummary;
import com.oa.identity.app.InFlightChecker.PendingTask;
import com.oa.identity.infra.row.InFlightItemRow;
import com.oa.identity.infra.row.PendingTaskRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 在途检查器单测（2a.3 的「顺带」项：把恒 0 的桩改成真实查询）。
 *
 * <p>验证四件事：
 * <ol>
 *   <li>口径：在途 = {@code flow_instance.status='approving'}（由 SQL 承担），
 *       待办 = {@code flow_task.status='pending'} —— 本层断言「查出来的数被原样汇总」；</li>
 *   <li>组织口径传 {@code sys_org.path}，路径归一化（补前导/尾随斜杠）；</li>
 *   <li>批量待办数是**一条 SQL**（不再逐条回退）；</li>
 *   <li>查询一律在 {@link DataScopeContext#system()} 下执行，且**执行后恢复原上下文**
 *       （影响面统计不能因调用人数据域漏算，也不能污染调用线程的数据域）。</li>
 * </ol>
 */
class DefaultInFlightCheckerTest {

    private InFlightQueryMapper mapper;
    private DefaultInFlightChecker checker;

    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    private void setUp(boolean withContext) {
        mapper = mock(InFlightQueryMapper.class);
        checker = new DefaultInFlightChecker(mapper);
        if (withContext) {
            DataScopeContext.set(DataScopeContext.builder()
                    .system(false)
                    .scopes(java.util.EnumSet.of(com.oa.common.scope.DataScopeType.SELF))
                    .build());
        }
    }

    @Test
    @DisplayName("组织口径：在途单据数 + 单号清单进汇总；路径前缀归一为 /1/12/ 形态")
    void checkOrgSubtree() {
        setUp(false);
        when(mapper.countOrgInFlight(eq("/1/12/"))).thenReturn(3);
        when(mapper.selectOrgInFlightBizNos(eq("/1/12/"), anyInt()))
                .thenReturn(List.of("OA-2026-000001", "OA-2026-000002", "OA-2026-000003"));

        InFlightSummary summary = checker.checkOrgSubtree("/1/12");

        assertThat(summary.inFlightInstances()).isEqualTo(3);
        assertThat(summary.pendingTasks()).isZero();
        assertThat(summary.bizNos()).hasSize(3);
        assertThat(summary.hasInFlight()).isTrue();
        assertThat(summary.total()).isEqualTo(3);
        verify(mapper).countOrgInFlight("/1/12/");
    }

    @Test
    @DisplayName("组织口径：无在途 → 不查单号清单（省一次查询），返回 none()")
    void checkOrgSubtreeEmpty() {
        setUp(false);
        when(mapper.countOrgInFlight(any())).thenReturn(0);

        InFlightSummary summary = checker.checkOrgSubtree("/1/12/");
        assertThat(summary.hasInFlight()).isFalse();
        assertThat(summary.bizNos()).isEmpty();
        verify(mapper, never()).selectOrgInFlightBizNos(any(), anyInt());
    }

    @Test
    @DisplayName("组织口径：缺路径前缀 → 直接返回无在途并告警（不猜全库口径）")
    void checkOrgSubtreeWithoutPrefix() {
        setUp(false);
        assertThat(checker.checkOrgSubtree(null).hasInFlight()).isFalse();
        assertThat(checker.checkOrgSubtree("  ").hasInFlight()).isFalse();
        verify(mapper, never()).countOrgInFlight(any());
    }

    @Test
    @DisplayName("人员口径：名下待办数进 pendingTasks（不重复计入在途单据数，避免 total 翻倍）")
    void checkUser() {
        setUp(false);
        when(mapper.countUserPendingTasks(204L)).thenReturn(2);
        when(mapper.selectUserPendingBizNos(eq(204L), anyInt()))
                .thenReturn(List.of("OA-2026-100003", "OA-2026-100004"));

        InFlightSummary summary = checker.checkUser(204L);

        assertThat(summary.pendingTasks()).isEqualTo(2);
        assertThat(summary.inFlightInstances()).isZero();
        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.bizNos()).containsExactly("OA-2026-100003", "OA-2026-100004");
    }

    @Test
    @DisplayName("清单口径：待办清单 / 人员影响清单 / 组织影响清单逐字段映射")
    void listViews() {
        setUp(false);
        PendingTaskRow task = new PendingTaskRow();
        task.setTaskId(11L);
        task.setBizNo("OA-2026-100003");
        task.setNodeName("③分公司分管领导");
        task.setCreatedAt("2026-10-03 10:00:00");
        when(mapper.selectUserPendingTasks(eq(203L), anyInt())).thenReturn(List.of(task));

        InFlightItemRow item = new InFlightItemRow();
        item.setInstanceId(9L);
        item.setBizNo("OA-2026-100003");
        item.setFormType("fund");
        item.setInitiatorName("张三");
        item.setCurrentNodeName("③分公司分管领导");
        item.setStatus("pending");
        when(mapper.selectUserInFlightItems(eq(203L), anyInt())).thenReturn(List.of(item));
        when(mapper.selectOrgInFlightItems(eq("/1/12/"), anyInt())).thenReturn(List.of(item));

        List<PendingTask> tasks = checker.pendingTasksOf(203L);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).taskId()).isEqualTo(11L);
        assertThat(tasks.get(0).bizNo()).isEqualTo("OA-2026-100003");
        assertThat(tasks.get(0).nodeName()).isEqualTo("③分公司分管领导");

        List<InFlightItem> items = checker.inFlightItems(203L);
        assertThat(items).hasSize(1);
        assertThat(items.get(0))
                .extracting(InFlightItem::instanceId, InFlightItem::bizNo, InFlightItem::formType,
                        InFlightItem::initiatorName, InFlightItem::currentNodeName, InFlightItem::status)
                .containsExactly(9L, "OA-2026-100003", "fund", "张三", "③分公司分管领导", "pending");

        assertThat(checker.orgInFlightItems("1/12")).hasSize(1);
        assertThat(checker.inFlightItems(null)).isEmpty();
        assertThat(checker.pendingTasksOf(null)).isEmpty();
        assertThat(checker.orgInFlightItems(null)).isEmpty();
    }

    @Test
    @DisplayName("批量待办数：**一条 SQL** 取回并按 0 补齐未命中的用户（列表页禁止 N+1）")
    void pendingTaskCounts() {
        setUp(false);
        PendingTaskRow row = new PendingTaskRow();
        row.setAssigneeId(204L);
        row.setTotal(2);
        when(mapper.selectPendingTaskCounts(any())).thenReturn(List.of(row));

        Map<Long, Integer> counts = checker.pendingTaskCounts(
                java.util.Arrays.asList(204L, 205L, 204L, null));

        assertThat(counts).containsEntry(204L, 2).containsEntry(205L, 0);
        assertThat(counts).hasSize(2);
        verify(mapper).selectPendingTaskCounts(any());
        verify(mapper, never()).countUserPendingTasks(any());
    }

    @Test
    @DisplayName("数据域纪律：查询在 system 口径下执行，且执行后**恢复**调用线程原上下文")
    void queriesRunUnderSystemScopeAndRestore() {
        setUp(true);
        DataScopeContext original = DataScopeContext.current();
        assertThat(original.isSystem()).isFalse();

        AtomicReference<Boolean> systemDuringQuery = new AtomicReference<>();
        when(mapper.countOrgInFlight(any())).thenAnswer(invocation -> {
            DataScopeContext during = DataScopeContext.current();
            systemDuringQuery.set(during != null && during.isSystem());
            return 1;
        });
        when(mapper.selectOrgInFlightBizNos(any(), anyInt())).thenReturn(List.of("OA-2026-000001"));

        checker.checkOrgSubtree("/1/12/");

        assertThat(systemDuringQuery.get())
                .as("影响面统计必须在系统口径下执行（不能因调用人数据域漏算在途单据）").isTrue();
        assertThat(DataScopeContext.current())
                .as("执行后必须恢复原上下文（不能污染调用线程）")
                .isSameAs(original);
    }

    @Test
    @DisplayName("路径归一化：/1/12、1/12、/1/12/ 三种写法等价")
    void prefixNormalization() {
        assertThat(DefaultInFlightChecker.normalizePrefix("/1/12")).isEqualTo("/1/12/");
        assertThat(DefaultInFlightChecker.normalizePrefix("1/12")).isEqualTo("/1/12/");
        assertThat(DefaultInFlightChecker.normalizePrefix("/1/12/")).isEqualTo("/1/12/");
        assertThat(DefaultInFlightChecker.normalizePrefix(null)).isNull();

        setUp(false);
        when(mapper.countOrgInFlight(eq("/1/12/"))).thenReturn(1);
        when(mapper.selectOrgInFlightBizNos(eq("/1/12/"), anyInt())).thenReturn(new ArrayList<>());
        for (String prefix : List.of("/1/12", "1/12", "/1/12/")) {
            assertThat(checker.checkOrgSubtree(prefix).inFlightInstances()).isEqualTo(1);
        }
    }
}
