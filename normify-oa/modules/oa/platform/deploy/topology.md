---
uid: 6ef27114
id: oa.platform.deploy.topology
parent: oa.platform.deploy
state: planned
name: {zh: "部署拓扑与容器", en: "Deployment Topology"}
description:
  zh: >
      私有化部署拓扑：容器组成、反向代理、数据库与文件存储挂载，以及存活探针。
      
  en: >
      Deployment topology for an on-premise install: container composition, reverse proxy, database and file storage mounts, plus a liveness probe.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.759Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 531
    end_line: 531
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
