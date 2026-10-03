package com.oa.form.dict;

import com.oa.form.dict.infra.SysDictItemMapper;
import com.oa.form.dict.infra.SysDictItemRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 内存版字典 Mapper（测试替身）——种子数据**逐行照抄** {@code doc/dict-seed.md}（V0.4 定稿 37 行）。
 *
 * <p>用途：让字典/校验相关的单测**不依赖 MySQL**，同时把「种子数据行数与取值」变成可断言的常量；
 * 一旦真源种子改动而这里没跟着改，{@link FormDictServiceTest} 的行数断言会立刻变红。
 *
 * <p>注意：本类**不是**生产代码，刻意放在 test 源码树；生产读写一律经
 * {@code mapper/form/SysDictItemMapper.xml}。
 */
public class InMemoryDictMapper implements SysDictItemMapper {

    private final Map<String, List<SysDictItemRow>> rows = new LinkedHashMap<>();

    public InMemoryDictMapper() {
        seed("matter_category", "business:经营:10", "economy:经济:20", "admin:行政:30",
                "hr:人力:40", "invest:投资:50");
        seed("contract_type", "purchase:购销:10", "service:服务:30", "lease:租赁:40",
                "construction:工程:50", "labor:劳务:60", "other:其他:999");
        seed("seal_type", "company_seal:公章:10", "contract_seal:合同章:20", "finance_seal:财务章:30",
                "legal_seal:法人章:40", "cert_seal:证照章:50", "cert_borrow:证照借用:60");
        seed("cert_type", "business_license:营业执照:10", "tax_cert:税务登记证:20",
                "org_code:组织机构代码证:30", "qualification:资质证书:40", "other:其他:999");
        seed("payment_method", "transfer:银行转账:10", "acceptance:银行承兑汇票:20",
                "cash:现金:30", "other:其他:999");
        seed("group_dept", "econ_dev:经发部:10", "finance:财务部:20", "hr_dept:人力资源部:30",
                "group_office:集团办:40");
        seed("review_dept_other", "econ_dev:经发部:10", "finance:财务部:20", "hr_dept:人力资源部:30",
                "group_office:集团办:40");
        seed("return_status", "pending:未归还:10", "returned:已归还:20", "not_required:无需归还:30");
    }

    private void seed(String dictType, String... items) {
        List<SysDictItemRow> list = new ArrayList<>();
        for (String item : items) {
            String[] parts = item.split(":");
            SysDictItemRow row = new SysDictItemRow();
            row.setDictType(dictType);
            row.setItemCode(parts[0]);
            row.setItemName(parts[1]);
            row.setItemNameEn(parts[0]);
            row.setSortNo(Integer.parseInt(parts[2]));
            row.setStatus("active");
            list.add(row);
        }
        rows.put(dictType, list);
    }

    /** 追加一项（含停用项，用于「停用项不可选」的用例）。 */
    public void add(String dictType, String code, String name, int sortNo, String status) {
        SysDictItemRow row = new SysDictItemRow();
        row.setDictType(dictType);
        row.setItemCode(code);
        row.setItemName(name);
        row.setSortNo(sortNo);
        row.setStatus(status);
        rows.computeIfAbsent(dictType, key -> new ArrayList<>()).add(row);
    }

    /** 删除一项（模拟后台删除字典项；历史单据取值不受影响）。 */
    public void remove(String dictType, String code) {
        List<SysDictItemRow> list = rows.get(dictType);
        if (list != null) {
            list.removeIf(row -> code.equals(row.getItemCode()));
        }
    }

    @Override
    public List<SysDictItemRow> selectEnabledByType(@Param("dictType") String dictType) {
        List<SysDictItemRow> result = new ArrayList<>();
        for (SysDictItemRow row : rows.getOrDefault(dictType, List.of())) {
            if ("active".equalsIgnoreCase(row.getStatus())) {
                result.add(row);
            }
        }
        return result;
    }

    @Override
    public List<SysDictItemRow> selectAllByType(@Param("dictType") String dictType) {
        return new ArrayList<>(rows.getOrDefault(dictType, List.of()));
    }

    @Override
    public List<Map<String, Object>> countByType() {
        List<Map<String, Object>> result = new ArrayList<>();
        rows.forEach((dictType, list) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dictType", dictType);
            entry.put("itemCount", list.size());
            result.add(entry);
        });
        return result;
    }

    /** 全部行数（自检：真源 37 行）。 */
    public int totalRows() {
        return rows.values().stream().mapToInt(List::size).sum();
    }
}
