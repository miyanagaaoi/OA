package com.oa.identity.app;

import com.oa.identity.api.dto.InFlightDtos;
import java.util.ArrayList;
import java.util.List;

/**
 * 在途影响清单的**端口 → 出参**映射（{@link InFlightChecker.InFlightItem} →
 * {@link InFlightDtos.InFlightItemView}）。
 *
 * <p>存在意义：人员侧（{@code UserService}）与组织侧（{@code OrgService}）共用同一行结构，
 * 映射只写一次；应用层依赖 DTO 层（{@code app → api.dto}）是既有方向，
 * 反向依赖（DTO 引用端口类型）则不会出现。
 */
public final class InFlightViews {

    private InFlightViews() {
    }

    /** 空清单的稳定表示（桩实现与「无在途」场景都走它，保证出参是 {@code []} 而不是 {@code null}）。 */
    public static List<InFlightDtos.InFlightItemView> views(List<InFlightChecker.InFlightItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<InFlightDtos.InFlightItemView> result = new ArrayList<>(items.size());
        for (InFlightChecker.InFlightItem item : items) {
            if (item == null) {
                continue;
            }
            result.add(new InFlightDtos.InFlightItemView(
                    item.instanceId(),
                    item.bizNo(),
                    item.formType(),
                    item.initiatorName(),
                    item.nodeName(),
                    item.currentNodeName(),
                    item.status()));
        }
        return result;
    }
}
