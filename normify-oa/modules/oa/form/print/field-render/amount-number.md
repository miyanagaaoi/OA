---
uid: b5ce533d
id: oa.form.print.field-render.amount-number
parent: oa.form.print.field-render
name: {zh: "金额与编号等宽格式", en: "Monospaced Amounts & Numbers"}
description:
  zh: >
      打印稿统一等宽字体（Consolas / Courier New）便于归档核对：金额带千分位与两位小数，≥100 万时同时显示万元换算（如 `1,250,000.00 ¥ / 125.00 万`）；单号、合同编号、申请编号均等宽；黑白复印后信息不得丢失。
      
  en: >
      Sheets use a monospaced face (Consolas / Courier New) for archival checking: amounts carry thousands separators and two decimals, and from 1,000,000 they also show the ten-thousand conversion (e.g. `1,250,000.00 ¥ / 125.00 万`); document, contract and application numbers are monospaced too, and no information may be lost in black-and-white copying.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.021Z"
fingerprint: 84e73298633e69866b44401911c474f18cd34d63ff8b4a46d09e2d711c341178
source:
  - path: "DESIGN.md"
    line: 999
    end_line: 1001
  - path: "doc/forms.md"
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/{instance_id}/amount-format"
    description:
      zh: >
          返回打印用金额与编号格式化结果。
          
      en: >
          Returns print-ready amount and number formatting.
          
  - protocol: file
    path: "templates/print/partials/mono-number.css"
    description:
      zh: >
          等宽数字与编号样式片段。
          
      en: >
          Stylesheet partial for monospaced amounts and numbers.
          
deps:
  - kind: reference
    to: oa.form.template.validate.amount
    from_api: "GET /api/v1/forms/print/{instance_id}/amount-format"
    to_api: "POST /api/v1/forms/amount/normalize"
    label: {zh: "定点金额格式", en: "Fixed-point amounts"}
---

## 证据锚点
- `doc/forms.md` → `### 1.5 金额字段的统一规则（全局）`（§1.5 金额字段的统一规则）
