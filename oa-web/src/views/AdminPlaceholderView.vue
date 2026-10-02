<script setup lang="ts">
/**
 * oa-web · 管理后台总览
 * ----------------------------------------------------------------------------
 * 来源：`doc/prd-0.1.md` 6.10（管理后台：用户与组织管理 / 流程配置 / 权限配置 /
 *       数据字典 / 报表 / 系统管理员兜底权限边界）
 *       + `doc/tech-design.md` §5.3（管理后台承接数据字典、权限树、流程与表单配置）
 *
 * 阶段 1 · 1.1 已落地两项：**组织架构**（组织树 + 负责人 + 岗位）与**人员管理**
 * （人员列表、一人多岗、离职/调岗/交接）；其余模块仍为占位。
 * 入口一律「无权限不渲染」（DESIGN.md Agent Usage Rules 第 6 条）。
 */
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { canManageOrg, canManageUser } from '@/utils/admin'

const userStore = useUserStore()

const ready = computed(() =>
  [
    {
      name: '组织架构',
      note: '组织树（四级）、改名/移动/启停、负责人正副职、集团层业务线绑定、停用在途检查',
      path: '/admin/orgs',
      visible: canManageOrg(userStore),
    },
    {
      name: '人员管理',
      note: '人员列表与筛选、一人多岗（主岗唯一）、离职/调岗/交接与影响清单、通讯录',
      path: '/admin/users',
      visible: canManageUser(userStore),
    },
  ].filter((item) => item.visible),
)

const modules = [
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
      <span class="oa-tag is-info">阶段 1 · 1.1 已落地组织与人员</span>
    </header>

    <p class="lead">
      组织架构与人员管理已按 <code>doc/prd-0.1.md</code> 5.1 / 5.5 与
      <code>doc/import-spec.md</code> 的中文↔code 口径实现；其余管理能力（REQ-ADMIN-002 ~ 005）
      排在后续阶段，承接方式见 <code>doc/tech-design.md</code> §5.3 与
      <code>normify-oa/modules/oa/admin/**</code>。
    </p>

    <ul v-if="ready.length" class="grid">
      <li v-for="item in ready" :key="item.name" class="oa-card is-ready">
        <b>{{ item.name }}</b>
        <span>{{ item.note }}</span>
        <RouterLink class="enter" :to="item.path">进入 →</RouterLink>
      </li>
    </ul>

    <h2 class="section">后续阶段</h2>

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

.section {
  margin-top: var(--oa-space-xs);
  font: var(--oa-font-title-section);
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

/* 已落地模块用主色描边区分（不新增色值，仅复用 primary-border） */
.grid li.is-ready {
  border-color: var(--oa-color-primary-border);
}

.grid b {
  font: var(--oa-font-title-section);
  color: var(--oa-color-ink);
}

.grid span {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.enter {
  margin-top: var(--oa-space-xs);
  font: var(--oa-font-label);
  color: var(--oa-color-primary);
}
</style>
