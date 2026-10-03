package com.oa.form.dict;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.form.dict.infra.SysDictItemMapper;
import com.oa.form.dict.infra.SysDictItemRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * <b>2b.4 字典与下拉</b> —— 表单侧消费 {@code sys_dict_item} 的唯一入口。
 *
 * <h2>三条硬口径（逐条取证）</h2>
 * <ol>
 *   <li><b>下拉取值来自字典，不得硬编码</b>：{@code doc/forms.md} §6 的「字段 code ↔ 字典类型对照」表 +
 *       {@code doc/forms.md} AC-03「随后在后台数据字典中新增一个分类，表单中即刻可选」。
 *       因此选项一律 {@code SELECT ... FROM sys_dict_item}，代码里**不出现**任何
 *       {@code matter_category} 的取值常量表（{@code doc/dict-seed.md} §10 硬约束 1：
 *       「同一值域只允许一处定义 … 代码中不得再出现 {@code ContractType} 枚举常量表」）。</li>
 *   <li><b>类别为配置项</b>：{@code doc/prd-0.1.md} §6.1「事项类别是配置项，仅作分类标签」+
 *       {@code doc/forms.md} §6.1「**配置项**：由管理后台维护，可增删（PRD REQ-ADMIN-004）」。
 *       新增字典项**无需发版**；本服务不缓存「白名单取值」，只缓存行本身（见 {@link #refresh()}）。</li>
 *   <li><b>历史单据保留当时取值（快照语义）</b>：{@code doc/enums.md} §10.1 约束 4
 *       「{@code flow_instance.category} 为**快照字段**，不随字典改名而变；字典改名只影响展示」+
 *       {@code doc/templates.md} §4.2「修改字典选项（增删选项）→ 在途实例影响：无 —
 *       字段值为快照存储，历史单据显示原 code 的中文名」。
 *       落点：值（code）写 {@code form_data.fields_json}，展示名由 {@link #snapshotNames} 在**读取时**
 *       解析；字典后续改名/停用**不回溯**已落库的 code。</li>
 * </ol>
 *
 * <h2>为什么未知 {@code dict_type} 直接 400 而不是返回空列表</h2>
 * <p>拼错的类型名返回空列表 ⇒ 前端「这个字段没选项」+ 服务端 {@code inDict} 把所有取值判非法，
 * 故障现象与「字典真被清空」无法区分。故按 {@link DictType} 白名单 fail-closed。
 */
@Service
public class FormDictService {

    private static final Logger log = LoggerFactory.getLogger(FormDictService.class);

    private final SysDictItemMapper mapper;
    /** 启用项缓存：{@code dict_type → 启用项}（仅缓存**行**，不缓存取值白名单语义）。 */
    private final Map<String, List<DictItem>> enabledCache = new ConcurrentHashMap<>();

    public FormDictService(SysDictItemMapper mapper) {
        this.mapper = mapper;
    }

    // ================================================================ 读取

    /**
     * 下拉取值（**启用项**）。未知字典类型 → 400 {@link ErrorCode#PARAM_INVALID}。
     *
     * @throws BizException 400 未知 {@code dict_type}
     */
    public List<DictItem> options(String dictType) {
        DictType type = requireType(dictType);
        return enabledCache.computeIfAbsent(type.code(), key -> {
            List<DictItem> items = new ArrayList<>();
            for (SysDictItemRow row : mapper.selectEnabledByType(key)) {
                // 行本身就带 status='active' 过滤，这里再兜一层（历史数据可能大小写不一）
                if (row.toItem().enabled()) {
                    items.add(row.toItem());
                }
            }
            return Collections.unmodifiableList(items);
        });
    }

    /** 全部项（含停用；用于取值合法性判定与名称解析）。 */
    public List<DictItem> allItems(String dictType) {
        DictType type = requireType(dictType);
        List<DictItem> items = new ArrayList<>();
        for (SysDictItemRow row : mapper.selectAllByType(type.code())) {
            items.add(row.toItem());
        }
        return items;
    }

    /** 字典类型 → 行数（披露用；不参与判定）。 */
    public Map<String, Object> counts() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (DictType type : DictType.values()) {
            result.put(type.code(), allItems(type.code()).size());
        }
        return result;
    }

    /** 清空启用项缓存（后台改字典后显式失效；{@code POST /api/v1/forms/dicts/cache/refresh}）。 */
    public int refresh() {
        int size = enabledCache.size();
        enabledCache.clear();
        log.info("字典启用项缓存已失效（原缓存 {} 个字典类型）；下次读取将回源 sys_dict_item", size);
        return size;
    }

    // ================================================================ 取值判定

    /**
     * 取值是否属于该字典的**启用项**。
     *
     * <p>停用项与未知 code 一律判非法（{@code doc/dict-seed.md} 的 {@code status} 列语义：
     * 停用 = 不再可选）；未知 {@code dict_type} 同样判非法（调用方应先用 {@link #requireType}）。
     */
    public boolean isValidOption(String dictType, String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return false;
        }
        Optional<DictType> type = DictType.of(dictType);
        if (type.isEmpty()) {
            return false;
        }
        String normalized = itemCode.trim();
        for (DictItem item : options(type.get().code())) {
            if (item.itemCode().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /** 断言取值属于该字典的启用项，否则 400（文案带字典名与合法取值清单）。 */
    public DictItem requireOption(String dictType, String itemCode) {
        DictType type = requireType(dictType);
        String normalized = itemCode == null ? null : itemCode.trim();
        for (DictItem item : options(type.code())) {
            if (item.itemCode().equals(normalized)) {
                return item;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                String.format("「%s」取值非法：%s（字典 %s 的合法取值为 %s）",
                        type.label(), normalized, type.code(), enabledCodes(type.code())));
    }

    /**
     * 解析取值的中文名（**快照语义的读取侧**）。
     *
     * <p>字典里查不到该 code（例如历史单据用了后来被删除的选项）时返回 {@code code} 本身，
     * **绝不**返回 {@code null} 或抛异常 —— 历史单据必须仍然可读、可打印。
     */
    public String displayName(String dictType, String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return null;
        }
        String normalized = itemCode.trim();
        for (DictItem item : allItems(dictType)) {
            if (item.itemCode().equals(normalized)) {
                return item.itemName();
            }
        }
        return normalized;
    }

    /** 批量解析中文名（保序；多选字段的打印「逗号分隔」口径用它）。 */
    public List<String> displayNames(String dictType, Iterable<String> itemCodes) {
        List<String> names = new ArrayList<>();
        if (itemCodes == null) {
            return names;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String code : itemCodes) {
            if (code == null || code.isBlank() || !seen.add(code.trim())) {
                continue;
            }
            String name = displayName(dictType, code);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    /** 取值快照（`[{code, name}]`；值 + 当时的中文名一并留痕）。 */
    public List<Map<String, Object>> snapshotNames(String dictType, Iterable<String> itemCodes) {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        if (itemCodes == null) {
            return snapshot;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String code : itemCodes) {
            if (code == null || code.isBlank() || !seen.add(code.trim())) {
                continue;
            }
            String normalized = code.trim();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", normalized);
            entry.put("name", displayName(dictType, normalized));
            snapshot.add(entry);
        }
        return snapshot;
    }

    /** 合法取值清单（错误文案用）。 */
    public String enabledCodes(String dictType) {
        List<String> codes = new ArrayList<>();
        for (DictItem item : options(dictType)) {
            codes.add(item.itemCode());
        }
        return codes.isEmpty() ? "（空）" : String.join(" / ", codes);
    }

    // ================================================================ 内部

    /** 白名单校验（未知类型 → 400；文案列出 8 类合法值）。 */
    public DictType requireType(String dictType) {
        Optional<DictType> type = DictType.of(dictType);
        if (type.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    String.format("未知的字典类型「%s」：合法取值为 %s（doc/dict-seed.md §0.3 白名单）",
                            dictType == null ? "" : dictType.trim(), String.join(" / ", DictType.codes())))
                    .withDetail("dictType", dictType)
                    .withDetail("allowedDictTypes", DictType.codes());
        }
        return type.get();
    }

    /** 该字典是否含指定 code（含停用项；用于「历史取值仍可读」的判定）。 */
    public boolean containsCode(String dictType, String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return false;
        }
        String normalized = itemCode.trim().toLowerCase(Locale.ROOT);
        for (DictItem item : allItems(dictType)) {
            if (item.itemCode().toLowerCase(Locale.ROOT).equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
