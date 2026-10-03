---
uid: 1f79d071
id: oa.portal.initiate.toc
parent: oa.portal.initiate
state: planned
name: {zh: "本页导航吸附目录", en: "In-page Section Index"}
description:
  zh: >
      ≥1440px 时表单右侧出现的吸附目录（宽 140px）：列出全部分区标题，当前分区用 primary 文字色高亮，点击滚动定位；窄屏隐藏该目录，导航职责交回分区标题带。
      
  en: >
      A 140px sticky in-page index on the right of the form from 1440px up: it lists all section titles, highlights the current section in primary text colour and scrolls to a section on click; on narrower screens it disappears and the section header bands take over navigation.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.394Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 863
    end_line: 863
  - path: "DESIGN.md"
    line: 786
    end_line: 789
apis:
  - protocol: http
    method: GET
    path: "/initiate/{form_type}/outline"
    description:
      zh: >
          表单分区目录页（吸附目录的数据来源）。
          
      en: >
          Form outline route that feeds the in-page index.
          
deps:
  - kind: reference
    to: oa.design.token
    label: {zh: "面板宽度与吸附布局令牌", en: "Panel & sticky layout tokens"}
---
