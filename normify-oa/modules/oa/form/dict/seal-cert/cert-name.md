---
uid: 5f943ff2
id: oa.form.dict.seal-cert.cert-name
parent: oa.form.dict.seal-cert
name: {zh: "证照名称字典", en: "Certificate Name Dictionary"}
description:
  zh: >
      证照名称 cert_name：business_license 营业执照 / tax_cert 税务登记证 / org_code 组织机构代码证 / qualification 资质证书 / other 其他；seal_type=证照借用 时必填。
      
  en: >
      Certificate name `cert_name`: business license, tax registration certificate, organization code certificate, qualification certificate and other; required when seal_type is certificate borrow.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.008Z"
fingerprint: 10c6effd215627f1c844a3acecef0995e0cae341763861c18553845cb71394d6
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/cert-name/items"
    description:
      zh: >
          证照名称可选值列表。
          
      en: >
          Lists selectable certificate names.
          
---

## 证据锚点
- `doc/forms.md` → `### 6.5 证照类型（字段 code `cert_name` · 字典类型 `cert_type`）`（§6.5 证照类型）
