---
uid: 6ef27114
id: oa.platform.deploy.topology
parent: oa.platform.deploy
name: {zh: "部署拓扑与容器", en: "Deployment Topology"}
description:
  zh: >
      私有化部署拓扑：容器组成、反向代理、数据库与文件存储挂载，以及存活探针。
      
  en: >
      Deployment topology for an on-premise install: container composition, reverse proxy, database and file storage mounts, plus a liveness probe.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.097Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: file
    path: "deploy/docker-compose.yml"
    description:
      zh: >
          私有化部署容器编排。
          
      en: >
          Container composition for the on-premise deployment.
          
  - protocol: file
    path: "deploy/nginx/oa.conf"
    description:
      zh: >
          反向代理与静态资源站点配置。
          
      en: >
          Reverse proxy and static site configuration.
          
  - protocol: http
    method: GET
    path: "/healthz"
    description:
      zh: >
          部署存活探针。
          
      en: >
          Deployment liveness probe.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-001`（§第9章 非功能需求）
