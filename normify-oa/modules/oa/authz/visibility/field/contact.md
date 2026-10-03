---
uid: 849bd261
id: oa.authz.visibility.field.contact
parent: oa.authz.visibility.field
name: {zh: "联系方式脱敏", en: "Contact Masking"}
description:
  zh: >
      手机号在通讯录中默认脱敏为 138****8888，仅本人与系统管理员可见完整值；对外提供统一的脱敏渲染契约，供通讯录、单据详情与 H5 复用。
      
  en: >
      Phone numbers are masked as 138****8888 in the directory and only the owner or a system admin may see the full value; a single masking contract is exposed for the directory, document detail and H5 surfaces.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.416Z"
fingerprint: ddc04a36cdc4ba75d3e0a941a2bfc318dd2517641db09187b9c00bd3a779a9e6
source:
  - path: "oa-server/src/main/java/com/oa/authz/visibility/PhoneVisibilityService.java"
  - path: "oa-server/src/main/java/com/oa/authz/api/FieldPolicyController.java"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/field-policy/contact"
    description:
      zh: >
          读取联系方式脱敏策略（138****8888 形态与加密算法口径）。
          
      en: >
          Reads the contact masking policy.
          
  - protocol: rpc
    path: "authz.visibility.mask.phone"
    description:
      zh: >
          按调用者身份返回脱敏或完整手机号。
          
      en: >
          Returns a masked or full phone by caller identity.
          
deps:
  - kind: call
    to: oa.authz.scope.isolation
    from_api: "rpc:authz.visibility.mask.phone"
    to_api: "rpc:authz.scope.assertAccess"
    label: {zh: "判定本人或系统管理员", en: "Check caller identity"}
---
