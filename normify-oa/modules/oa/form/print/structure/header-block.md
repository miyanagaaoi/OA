---
uid: a758de8c
id: oa.form.print.structure.header-block
parent: oa.form.print.structure
state: planned
name: {zh: "抬头与三栏表头", en: "Heading & Three-Column Header"}
description:
  zh: >
      单据抬头：居中、加粗、字号 16pt、字距 2px，下方可带副标题；三栏表头行如「提报单位 | 责任部门 | 报送时间」，标签列居中等宽。
      
  en: >
      Document heading: centred, bold, 16pt with 2px letter spacing and an optional subtitle; the three-column header row such as reporting unit | responsible department | submission time, with centred equal-width label cells.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.671Z"
fingerprint: ac0ee794ab636d5631ecf55f3fe0063493d1e3cafae26bb19c1ca5c017904aea
source:
  - path: "DESIGN.md"
    line: 1005
    end_line: 1006
apis:
  - protocol: file
    path: "templates/print/partials/header-block.html"
    description:
      zh: >
          打印抬头与三栏表头片段。
          
      en: >
          Partial for the print heading and three-column header.
          
---
