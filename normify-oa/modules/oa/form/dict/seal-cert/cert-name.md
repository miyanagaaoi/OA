---
uid: 5f943ff2
id: oa.form.dict.seal-cert.cert-name
parent: oa.form.dict.seal-cert
state: planned
name: {zh: "证照名称字典", en: "Certificate Name Dictionary"}
description:
  zh: >
      证照名称 cert_name：business_license 营业执照 / tax_cert 税务登记证 / org_code 组织机构代码证 / qualification 资质证书 / other 其他；seal_type=证照借用 时必填。
      
  en: >
      Certificate name `cert_name`: business license, tax registration certificate, organization code certificate, qualification certificate and other; required when seal_type is certificate borrow.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.632Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 225
    end_line: 233
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
