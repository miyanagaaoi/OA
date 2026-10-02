# oa-deploy · 交付与初始化资产

本目录存放**可执行交付物**：初始化 SQL、批量导入模板，以及后续的环境与容器编排文件（`nginx/`、`docker-compose.yml`、`config/`）。

> 真源是文档：**表结构改 `doc/data-model.md`、字典改 `doc/dict-seed.md`、模板改 `doc/templates.md`**，然后重新生成/校验，不要手工改生成物。

---

## 1. 目录

| 路径 | 内容 | 来源 |
| --- | --- | --- |
| `sql/01-schema.sql` | 27 张表 + 索引 + CHECK + 外键 + **不可篡改触发器**（拒 UPDATE/DELETE） | 由 `doc/data-model.md` 生成 |
| `sql/02-dict-seed.sql` | 数据字典种子：8 个 `dict_type` / 37 项（幂等） | 由 `doc/dict-seed.md` 生成 |
| `sql/03-templates.sql` | 四类单据的流程模板（4 条）+ 节点定义（4 × 7 = 28 条）+ `form_schema_json`（幂等） | 依据 `doc/templates.md` / `doc/forms.md` 编写 |
| `import/*.csv` | 组织 / 人员 / 负责人 / 一人多岗 的批量导入模板（UTF-8 带 BOM，可用 Excel 直接打开） | 依据 `doc/import-spec.md` |

---

## 2. 执行顺序（初始化一套环境）

```bash
# 1) 建库（示例）
mysql -uroot -p -e "CREATE DATABASE oa DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"

# 2) 表结构与不可篡改约束
mysql -uroot -p oa < oa-deploy/sql/01-schema.sql

# 3) 数据字典种子（可重复执行）
mysql -uroot -p oa < oa-deploy/sql/02-dict-seed.sql

# 4) 四类单据的流程模板与表单模板
mysql -uroot -p oa < oa-deploy/sql/03-templates.sql

# 5) 组织与人员：先用模板填好 CSV，再按 import-spec 的顺序导入
#    组织 → 人员 → 负责人 → 一人多岗 → 角色分配
```

**幂等性**：`02` 与 `03` 均可重复执行（`INSERT ... ON DUPLICATE KEY UPDATE`）；`01` 为纯建表脚本，重复执行前请确认目标库为空。
**不可篡改**：`01` 中的触发器会拒绝对 `sys_log` / `flow_signature` / `sys_thread` 的 UPDATE 与 DELETE（验收 AC-20）；迁移 PostgreSQL 时改用 `REVOKE UPDATE, DELETE`（见 `doc/data-model.md` 第 9 章）。

---

## 3. 重新生成与校验

```bash
# 依据文档重新生成 01 / 02（文档改了就跑这一步）
node tools/gen-init-sql.js

# 只校验不落盘（CI 用）
node tools/gen-init-sql.js --check          # 27 张表 / 4 触发器 / 字典白名单 / 布尔字段不建字典项
node tools/check-ddl.js doc/data-model.md   # DDL 结构（主键、外键目标、重复列、金额非浮点）
node tools/check-templates-sql.js oa-deploy/sql/03-templates.sql
node tools/check-import-csv.js oa-deploy/import
node tools/validate-design-md.js DESIGN.md
```

以上命令已接入 [`.github/workflows/docs-ci.yml`](../.github/workflows/docs-ci.yml)，每次推送自动执行。

---

## 4. 上线前自检（部署到私有化环境后）

```sql
-- 模板与节点数量
SELECT code, version, node_count, JSON_VALID(form_schema_json) AS schema_ok FROM flow_template ORDER BY code;
SELECT t.code, COUNT(*) AS nodes FROM flow_node n JOIN flow_template t ON t.id = n.template_id GROUP BY t.code;
--  期望：4 行模板，每类 7 个节点，schema_ok = 1

-- 字典行数
SELECT dict_type, COUNT(*) FROM sys_dict_item GROUP BY dict_type ORDER BY dict_type;
--  期望：8 个类型 / 37 行

-- 不可篡改约束生效（应全部失败）
UPDATE sys_log SET action = 'x' WHERE id = 1;
DELETE FROM flow_signature WHERE id = 1;
```

> 兼容性提示：`import/*.csv` 带 UTF-8 BOM，便于 Excel 正确识别中文；若用脚本读取，请按 `utf-8-sig` 解码。
