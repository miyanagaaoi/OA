package com.oa.workflow.definition.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.TemplateStatus;
import com.oa.workflow.definition.domain.FlowTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 模板版本状态机与「在途锁版本」判定 —— <b>纯函数</b>。
 *
 * <h2>权威口径（逐条）</h2>
 * <ul>
 *   <li><b>V-01</b> 发布新版本不覆盖历史：按 {@code (code, version)} 唯一累积，旧版本保留可查；</li>
 *   <li><b>V-02 在途实例锁版本</b>：实例发起时锁定 {@code template_version}，剩余节点全部按该版本执行
 *       （REQ-FLOW-006 / AC-09）；</li>
 *   <li><b>V-05</b> {@code archived} 只阻止**新实例**使用，在途实例继续执行；</li>
 *   <li><b>V-08</b> 版本号单调递增：不得跳号、不得复用；</li>
 *   <li><b>§3.3</b> {@code draft} 可编辑；{@code published} / {@code archived} <b>只读</b>；
 *       同一 {@code code} 下最多一个 {@code published}；</li>
 *   <li><b>§4.1 第 5 步</b>：发布新版本时，原 {@code published} 版本转 {@code archived}；
 *       <b>§4.3</b>：不得删除已产生的模板版本（在途实例会失去执行依据）。</li>
 * </ul>
 *
 * <p>本类刻意只做判定与「下一个该变成什么状态」的计算，**不改数据**：所有写操作由
 * {@code FlowDefinitionService} 在同一事务内完成。
 */
public final class TemplateVersionPolicy {

    private TemplateVersionPolicy() {
    }

    // ================================================================ 可编辑性

    /** 只有草稿可编辑；已发布 / 已归档一律 409（templates.md §3.3 / §4.3）。 */
    public static void assertEditable(FlowTemplate template) {
        if (template == null) {
            throw BizException.notFound("流程模板");
        }
        TemplateStatus status = template.statusEnum();
        if (status == null) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "模板状态非法：" + template.getStatus());
        }
        if (status.readOnly()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_IMMUTABLE,
                    "模板 " + template.getCode() + " v" + template.getVersion() + " 状态为 "
                            + status.code() + "，只读；请先基于它发布新版本（POST /flow-templates/"
                            + template.getId() + "/versions）")
                    .withDetail("templateId", template.getId())
                    .withDetail("status", status.code());
        }
    }

    // ================================================================ 版本号

    /** 下一个版本号 = 现有最大版本 + 1（V-08：不得跳号、不得复用）。 */
    public static int nextVersion(List<FlowTemplate> sameCode) {
        int max = 0;
        if (sameCode != null) {
            for (FlowTemplate template : sameCode) {
                if (template != null && template.getVersion() != null) {
                    max = Math.max(max, template.getVersion());
                }
            }
        }
        return max + 1;
    }

    /** 断言新版本号**未与本单据类型的任何历史版本复用**（V-08）。 */
    public static void assertVersionFresh(List<FlowTemplate> sameCode, int candidate) {
        if (sameCode == null) {
            return;
        }
        for (FlowTemplate template : sameCode) {
            if (template != null && template.getVersion() != null && template.getVersion() == candidate) {
                throw new BizException(ErrorCode.FLOW_DRAFT_ALREADY_EXISTS,
                        "版本号 " + candidate + " 已存在（V-08：版本号不得复用、不得跳号）");
            }
        }
    }

    /** 当前草稿（同一 {@code code} 下最多一个可编辑草稿）。 */
    public static FlowTemplate draft(List<FlowTemplate> sameCode) {
        if (sameCode == null) {
            return null;
        }
        return sameCode.stream()
                .filter(item -> item != null && item.statusEnum() == TemplateStatus.DRAFT)
                .findFirst()
                .orElse(null);
    }

    /** 当前已发布版本（同一 {@code code} 下最多一个）。 */
    public static FlowTemplate published(List<FlowTemplate> sameCode) {
        if (sameCode == null) {
            return null;
        }
        return sameCode.stream()
                .filter(item -> item != null && item.statusEnum() == TemplateStatus.PUBLISHED)
                .findFirst()
                .orElse(null);
    }

    /** 按版本号倒序列出（版本历史接口的排序口径）。 */
    public static List<FlowTemplate> byVersionDesc(List<FlowTemplate> sameCode) {
        List<FlowTemplate> result = new ArrayList<>(sameCode == null ? List.of() : sameCode);
        result.sort(Comparator.comparing(FlowTemplate::getVersion,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    // ================================================================ 在途锁版本

    /**
     * <b>在途锁版本</b>的唯一读取口径：按 {@code (code, version)} 取模板。
     *
     * <p>实例侧只用 {@code flow_instance.template_id}（发起时写入的**那一行**）读取，
     * 因此「新版本发布后修改了模板」不会改变在途实例的执行依据（V-02 / AC-09）。
     * 本方法只用于校验「锁定的版本仍然存在」——{@code §4.3} 明确禁止删除模板版本，
     * 因此版本缺失属数据完整性事故，必须显式暴露而不是静默回退到最新版本。
     */
    public static FlowTemplate requireLocked(List<FlowTemplate> sameCode, int version, String code) {
        Optional<FlowTemplate> found = (sameCode == null ? List.<FlowTemplate>of() : sameCode).stream()
                .filter(item -> item != null && item.getVersion() != null && item.getVersion() == version)
                .findFirst();
        return found.orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND,
                "在途实例锁定的模板版本不存在：" + code + " v" + version
                        + "（templates.md §4.3 禁止删除已产生的模板版本）")
                .withDetail("code", code)
                .withDetail("version", version));
    }

    /** 新实例只能使用 {@code published} 版本（V-05 / §3.3）。 */
    public static void assertUsableForNewInstance(FlowTemplate template) {
        if (template == null) {
            throw BizException.notFound("流程模板");
        }
        TemplateStatus status = template.statusEnum();
        if (status == null || !status.usableByNewInstance()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "模板 " + template.getCode() + " v" + template.getVersion() + " 状态为 "
                            + template.getStatus() + "，不可用于新实例（仅 published 可用于发起）");
        }
    }
}
