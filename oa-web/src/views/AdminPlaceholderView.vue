<script setup lang="ts">
/**
 * oa-web · 管理后台占位页
 * ----------------------------------------------------------------------------
 * 来源：`DESIGN.md` › Layout（主导航"一期只预留审批中心一个模块入口"）
 *       + `doc/tech-design.md` §5.3（管理后台承接数据字典、权限树、流程与表单配置）
 *
 * 一期只保留入口与占位说明，不实现功能；无权限时不渲染入口（权限不可见优于不可用）。
 */
const modules = [
  { name: '组织与人员', note: '组织树、负责人绑定、人员与岗位、Excel 批量导入与影响面预览' },
  { name: '角色与权限树', note: '角色、权限树勾选、数据域（self / dept / company / group_all / group_category）' },
  { name: '流程模板与节点', note: '7 节点定义、决议模式、超时、加签与流转策略、版本发布' },
  { name: '表单模板', note: '四类单据字段字典、三态白名单、校验规则、打印标签映射' },
  { name: '数据字典', note: '事项类别、合同类型、用印类型、证照类型、付款方式、其他会审部门、归还状态' },
  { name: '运行期配置', note: '决议模式、闸门次数、补件上限、强制签名节点、会话与锁定、保留期、导出权限' },
  { name: '报表', note: '流程量、平均耗时、超时率、审批人效率、驳回率（导出留痕）' },
  { name: '审计与安全', note: '操作日志、权限变更与登录日志、不可篡改校验、会话与设备' },
]
</script>

<template>
  <div class="oa-admin">
    <header class="head">
      <h1>管理后台</h1>
      <span class="oa-tag is-info">一期占位</span>
    </header>

    <p class="lead">
      本页在一期只保留路由入口。管理后台的完整能力（REQ-ADMIN-001 ~ REQ-ADMIN-005）排在后续阶段，
      承接方式见 <code>doc/tech-design.md</code> §5.3 与 <code>normify-oa/modules/oa/admin/**</code>。
    </p>

    <ul class="grid">
      <li v-for="item in modules" :key="item.name" class="oa-card">
        <b>{{ item.name }}</b>
        <span>{{ item.note }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.oa-admin {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
}

.head {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
}

h1 {
  font: var(--oa-font-title-page);
  letter-spacing: var(--oa-letter-spacing-title-page);
  color: var(--oa-color-ink);
}

.lead {
  max-width: 860px;
  font: var(--oa-font-body);
  color: var(--oa-color-ink-muted);
}

code {
  padding: 0 4px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas-subtle);
  font: var(--oa-font-mono);
  color: var(--oa-color-ink);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--oa-space-md);
}

.grid li {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.grid b {
  font: var(--oa-font-title-section);
  color: var(--oa-color-ink);
}

.grid span {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}
</style>
