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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.713Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
