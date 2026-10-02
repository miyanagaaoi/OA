package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.identity.app.PositionPolicy.Assignment;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 岗位任职规则（**纯逻辑单测**）：一人多岗、主岗唯一、主岗切换时的自动降级
 * （import-spec E-POS-004 / E-POS-005；施工要求第 5 条）。
 */
class PositionPolicyTest {

    private static Assignment position(Long id, Long userId, Long orgId, boolean primary) {
        return new Assignment(id, userId, orgId, primary, "专员");
    }

    @Test
    @DisplayName("主岗切换：设新主岗时只降级该人的旧主岗，其它人不受影响")
    void demotionsOnlyForSameUser() {
        List<Assignment> existing = List.of(
                position(1L, 7L, 100L, true),
                position(2L, 7L, 200L, false),
                position(3L, 8L, 100L, true));

        List<Long> demotions = PositionPolicy.demotionsFor(existing, 7L, null);
        assertThat(demotions).containsExactly(1L);

        // 把既有的第 2 条升为主岗：需要降级第 1 条，且不含目标记录自身
        List<Long> switchDemotions = PositionPolicy.demotionsFor(existing, 7L, 2L);
        assertThat(switchDemotions).containsExactly(1L);

        // 新增一行（targetPositionId=null）：同样只降级第 1 条
        assertThat(PositionPolicy.demotionsFor(existing, 8L, null)).containsExactly(3L);
        assertThat(PositionPolicy.demotionsFor(existing, 99L, null)).isEmpty();
    }

    @Test
    @DisplayName("主岗查询与自洽校验：同一人多主岗即冲突（E-POS-005）")
    void primaryOfAndAudit() {
        List<Assignment> good = List.of(
                position(1L, 7L, 100L, true),
                position(2L, 7L, 200L, false),
                position(3L, 8L, 100L, true));
        PositionPolicy.assertSinglePrimary(good);
        assertThat(PositionPolicy.primaryOf(good, 7L)).get().extracting(Assignment::orgId).isEqualTo(100L);
        assertThat(PositionPolicy.primaryOf(good, 8L)).get().extracting(Assignment::id).isEqualTo(3L);
        assertThat(PositionPolicy.primaryOf(good, 42L)).isEmpty();

        List<Assignment> dirty = List.of(
                position(1L, 7L, 100L, true),
                position(2L, 7L, 200L, true));
        assertThatThrownBy(() -> PositionPolicy.assertSinglePrimary(dirty))
                .isInstanceOf(BizException.class).hasMessageContaining("多个主岗");
    }

    @Test
    @DisplayName("解除主岗后自动提升最早的剩余岗位；解除非主岗或无剩余岗位则不提升")
    void promotionAfterRemoval() {
        List<Assignment> before = List.of(
                position(1L, 7L, 100L, true),
                position(5L, 7L, 200L, false),
                position(9L, 7L, 300L, false));

        // 解除主岗 1 → 提升 id 最小的剩余岗位 5
        assertThat(PositionPolicy.promotionAfterRemoval(before, 7L, 1L)).contains(5L);
        // 解除非主岗 → 不提升
        assertThat(PositionPolicy.promotionAfterRemoval(before, 7L, 5L)).isEmpty();
        // 解除唯一岗位 → 无剩余，不提升
        assertThat(PositionPolicy.promotionAfterRemoval(List.of(position(1L, 7L, 100L, true)), 7L, 1L)).isEmpty();
        // 他人的岗位不受影响
        assertThat(PositionPolicy.promotionAfterRemoval(before, 8L, 1L)).isEmpty();
    }
}
