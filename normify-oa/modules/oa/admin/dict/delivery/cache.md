---
uid: 81a188db
id: oa.admin.dict.delivery.cache
parent: oa.admin.dict.delivery
state: planned
name: {zh: "缓存刷新", en: "Dictionary Cache"}
description:
  zh: >
      负责字典缓存的生命周期：手动刷新接口、缓存版本状态探查，以及表单层读取选项所用的 Redis 键布局。
      
  en: >
      Owns the dictionary cache lifecycle: a manual refresh endpoint, a status probe for the cache version, and the Redis key layout that the form layer reads options from.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.623Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 438
    end_line: 438
  - path: "doc/data-model.md"
    line: 256
    end_line: 267
apis:
  - protocol: http
    method: POST
    path: "/api/v1/admin/dict-cache/refresh"
    description:
      zh: >
          刷新字典缓存，使新增选项立即生效。
          
      en: >
          Refresh the dictionary cache so new options apply at once.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/dict-cache/status"
    description:
      zh: >
          查询缓存版本与最近刷新时间。
          
      en: >
          Read the cache version and last refresh time.
          
  - protocol: redis
    path: "dict:item:{dict_type}"
    description:
      zh: >
          按字典类型缓存的字典项键。
          
      en: >
          Cached dictionary items keyed by dictionary type.
          
deps:
  - kind: event
    to: oa.form.dict
    from_api: "POST /api/v1/admin/dict-cache/refresh"
    label: {zh: "选项变更广播", en: "Broadcast option changes"}
---
