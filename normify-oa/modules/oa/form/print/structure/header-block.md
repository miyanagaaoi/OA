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
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.594Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
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
