/**
 * oa-web · 三态读写规则（服务端白名单的前端镜像）
 * ----------------------------------------------------------------------------
 * 来源：`doc/tech-design.md` §5.4「三态读写与表单模板」
 *       + `normify-oa/modules/oa/form/template/write-model/state-whitelist.md`
 *       + `normify-oa/modules/oa/workflow/supplement/field-scope.md`
 *       + `normify-oa/modules/oa/form/seal/special/return-editable.md`
 *
 * 规则（唯一权威在服务端，这里只做渲染与提示，不替代服务端校验）：
 *   · 草稿（draft）    ：主字段全部可写
 *   · 审批中（approving）：主字段全部只读
 *   · 待补件（supplement）：**仅**「附件」+「补件说明」可写，主字段只读
 *   · 已办结（finished） ：全部只读
 *
 * **唯一例外**：印鉴证照审批单的 `return_status` / `return_date`，
 *   发起人与节点⑦（`archive_register` 登记归档）可以修改（PRD 6.6 + DESIGN 附录 C）。
 */
import type { DetailHeader, FormField, FormType, ReadWriteState } from '@/types/api'

/** 待补件期唯一可写的两个字段（tech-design §5.4） */
export const SUPPLEMENT_WRITABLE_FIELD_IDS = ['attachments', 'supplement_note'] as const

/** 印鉴证照单归还字段（三态只读的唯一例外） */
export const SEAL_RETURN_FIELD_IDS = ['return_status', 'return_date'] as const

/** 节点⑦：登记归档（enums.md §2，默认「仅登记不审批」） */
export const NODE_ARCHIVE_REGISTER_NO = 7

export interface ReadWriteContext {
  state: ReadWriteState
  formType: FormType
  /** 是否是本人发起的单据 */
  initiatedByMe: boolean
  /** 当前用户所处节点序号；⑦ 为登记归档 */
  currentNodeNo?: number
  /** 服务端下发的可写字段白名单（优先于本地规则） */
  writableFieldIds?: string[]
}

/** 从详情头部织出只读上下文 */
export function contextFromHeader(header: DetailHeader, writableFieldIds: string[] = []): ReadWriteContext {
  return {
    state: header.readWriteState,
    formType: header.formType,
    initiatedByMe: header.initiatedByMe,
    currentNodeNo: header.currentNodeNo,
    writableFieldIds,
  }
}

/** 印鉴单归还字段是否可改：发起人 或 节点⑦ */
export function isSealReturnEditable(ctx: ReadWriteContext): boolean {
  if (ctx.formType !== 'seal_cert') return false
  if (ctx.initiatedByMe) return true
  return ctx.currentNodeNo === NODE_ARCHIVE_REGISTER_NO
}

/** 三态下的主字段是否整体只读 */
export function isMainFormReadonly(state: ReadWriteState): boolean {
  return state !== 'draft'
}

export interface FieldEditability {
  editable: boolean
  /** 只读原因，用于字段旁的一行灰字与 tooltip（oa.authz.readonly-reasons） */
  reason?: string
}

/**
 * 单字段可编辑判定。
 * 优先级：印鉴单归还例外 > 服务端白名单 > 三态规则。
 * 例外必须排在白名单之前——tech-design §5.4 明确把 `return_status` /
 * `return_date` 定为"唯一例外"，即它不受任何三态限制。
 */
export function resolveFieldEditability(field: FormField, ctx: ReadWriteContext): FieldEditability {
  // 1) 印鉴单归还字段：三态只读的唯一例外（发起人与节点⑦可改）
  if ((SEAL_RETURN_FIELD_IDS as readonly string[]).includes(field.fieldId)) {
    return isSealReturnEditable(ctx)
      ? { editable: true }
      : { editable: false, reason: '仅发起人与节点⑦（登记归档）可修改归还状态与归还日期' }
  }

  // 2) 服务端白名单：白名单存在时以它为准
  if (ctx.writableFieldIds && ctx.writableFieldIds.length > 0) {
    const allowed = ctx.writableFieldIds.includes(field.fieldId)
    return allowed ? { editable: true } : { editable: false, reason: readonlyReason(field, ctx) }
  }

  // 3) 三态规则
  switch (ctx.state) {
    case 'draft':
      return { editable: true }
    case 'supplement':
      return (SUPPLEMENT_WRITABLE_FIELD_IDS as readonly string[]).includes(field.fieldId)
        ? { editable: true }
        : { editable: false, reason: '待补件期间主字段只读，仅可补充附件与说明' }
    case 'approving':
      return { editable: false, reason: '审批中字段全部只读，修改须由审批人驳回后重提' }
    case 'finished':
    default:
      return { editable: false, reason: '单据已办结，全部字段只读' }
  }
}

function readonlyReason(field: FormField, ctx: ReadWriteContext): string {
  if (ctx.state === 'supplement') {
    return '待补件期间仅可补充附件与说明'
  }
  if (ctx.state === 'approving') {
    return '审批中字段全部只读'
  }
  if (ctx.state === 'finished') {
    return '单据已办结，字段只读'
  }
  return field.readonlyReason || '当前状态不可编辑'
}

// ---------------------------------------------------------------------------
// 三态提示条模型（StateBanner.vue / PRD 6.6 异常路径）
// ---------------------------------------------------------------------------
export interface StateBannerModel {
  tone: 'info' | 'warning' | 'success' | 'neutral'
  title: string
  detail: string
  /** 是否展示「补件说明 + 附件」这两个可写入口 */
  showSupplementInputs: boolean
  /** 是否展示审批操作区 */
  showApprovalActions: boolean
  /** 是否展示印鉴单归还例外的说明 */
  showSealReturnException: boolean
}

export function describeState(ctx: ReadWriteContext): StateBannerModel {
  const sealException = ctx.formType === 'seal_cert' && isSealReturnEditable(ctx)

  switch (ctx.state) {
    case 'draft':
      return {
        tone: 'info',
        title: '草稿可编辑',
        detail: '本单据尚未提交，主字段全部可编辑；提交后字段将全部只读，修改须由审批人驳回后重提。',
        showSupplementInputs: false,
        showApprovalActions: false,
        showSealReturnException: sealException,
      }
    case 'approving':
      return {
        tone: 'info',
        title: '审批中只读',
        detail: '单据正在流转，主字段全部只读；审批人可在下方操作区填写意见并作出决议。',
        showSupplementInputs: false,
        showApprovalActions: true,
        showSealReturnException: sealException,
      }
    case 'supplement':
      return {
        tone: 'warning',
        title: '待补件：仅附件与说明可编辑',
        detail: '单据已暂停流转，主字段全部只读，仅「补件说明」与「附件」可编辑；补完回到请求节点，不计入驳回。',
        showSupplementInputs: true,
        showApprovalActions: false,
        showSealReturnException: sealException,
      }
    case 'finished':
    default:
      return {
        tone: 'success',
        title: '已办结只读',
        detail: '单据已结束流转，全部字段只读；如需变更请另行发起。',
        showSupplementInputs: false,
        showApprovalActions: false,
        showSealReturnException: sealException,
      }
  }
}

/** 三态 → 状态徽标（附录 B：草稿=neutral / 审批中=info / 待补件=warning / 办结=success） */
export function statePillClass(state: ReadWriteState): string {
  switch (state) {
    case 'draft':
      return 'is-closed'
    case 'approving':
      return 'is-processing'
    case 'supplement':
      return 'is-pending'
    case 'finished':
    default:
      return 'is-approved'
  }
}
