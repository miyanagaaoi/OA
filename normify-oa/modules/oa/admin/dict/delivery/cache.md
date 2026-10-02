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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.593Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
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
