<script setup lang="ts">
/**
 * oa-web · 统一状态徽标
 * ----------------------------------------------------------------------------
 * 来源：`DESIGN.md` 附录 B「状态与颜色映射表（全局唯一，实现时不得扩展）」
 *       + `doc/import-spec.md` §3 的枚举中文（启用/停用、在职/离职、正职/副职）
 *
 * 铁律：状态色只有五套语义（pending / approved / rejected / processing / closed），
 *       映射关系集中在 `utils/status.ts`，本组件只负责渲染 `.oa-pill` 样式，
 *       **不接受外部传入的颜色或类名**，避免各页面各写一套配色。
 */
import { computed } from 'vue'
import {
  leaderTypeStyle,
  orgStatusStyle,
  primaryPillStyle,
  userStatusStyle,
  type IdentityPillStyle,
} from '@/utils/status'
import type { LeaderType, OrgStatus, UserStatus } from '@/types/identity'

const props = withDefaults(
  defineProps<{
    /** 徽标语义域：组织状态 / 人员状态 / 负责人类型 / 岗位主岗 */
    kind: 'org' | 'user' | 'leader' | 'position'
    /** 对应枚举 code（position 域用 isPrimary） */
    status?: OrgStatus | UserStatus | LeaderType | string
    /** position 域：是否主岗（E-POS-005 每人最多一个主岗） */
    isPrimary?: boolean
    /** 覆盖文案（例如服务端回传的中文标签） */
    label?: string
  }>(),
  { status: '', isPrimary: false, label: '' },
)

const style = computed<IdentityPillStyle>(() => {
  switch (props.kind) {
    case 'org':
      return orgStatusStyle(props.status as OrgStatus)
    case 'user':
      return userStatusStyle(props.status as UserStatus)
    case 'leader':
      return leaderTypeStyle(props.status as LeaderType)
    case 'position':
    default:
      return primaryPillStyle(props.isPrimary)
  }
})

const text = computed(() => props.label || style.value.label)
</script>

<template>
  <span class="oa-pill" :class="style.pillClass">{{ text }}</span>
</template>
