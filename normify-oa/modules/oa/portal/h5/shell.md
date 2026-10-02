---
uid: 42270b29
id: oa.portal.h5.shell
parent: oa.portal.h5
state: planned
name: {zh: "H5 页面骨架", en: "H5 Page Shell"}
description:
  zh: >
      移动端 H5 骨架：白色顶部栏（48px）+ 白色内容区 + 白色底部操作栏，单列流式布局；禁止在 H5 上使用深色导航；≤480px 生效，H5 与桌面共用同一套设计令牌，只改变密度与控制尺寸。
      
  en: >
      The mobile H5 shell: a white 48px top bar over a white content area above a white bottom action bar in a single-column flow; dark navigation is forbidden on H5; the layout applies at 480px and below and shares one token set with the desktop, changing only density and control size.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.778Z"
fingerprint: ac0ee794ab636d5631ecf55f3fe0063493d1e3cafae26bb19c1ca5c017904aea
source:
  - path: "DESIGN.md"
    line: 790
    end_line: 791
  - path: "DESIGN.md"
    line: 934
    end_line: 934
  - path: "DESIGN.md"
    line: 610
    end_line: 610
apis:
  - protocol: http
    method: GET
    path: "/m/portal"
    description:
      zh: >
          H5 骨架的移动端首页路由。
          
      en: >
          Mobile home route for the H5 shell.
          
  - protocol: http
    method: GET
    path: "/api/v1/portal/h5/session-context"
    description:
      zh: >
          H5 骨架所需的当前设备、登录态与记住我上下文。
          
      en: >
          Current device, login state and remember-me context for the H5 shell.
          
deps:
  - kind: reference
    to: oa.design.token
    label: {zh: "与桌面端共用令牌", en: "Shared desktop tokens"}
---
