/**
 * oa-web · 审批中心（工作台 + 详情 + 打印）接口
 * ----------------------------------------------------------------------------
 * 接口路径与 `normify-oa/api-index.json` 契约逐条对齐：
 *
 * 工作台 oa.portal.workbench
 *   POST  /api/v1/portal/workbench/query                     列表查询（筛选 + 分页 + 批量）
 *   GET   /api/v1/portal/workbench/columns                   列定义
 *   GET   /api/v1/portal/workbench/filters                   筛选项字典
 *   GET   /api/v1/portal/workbench/summary                   四个 Tab 的计数
 *   POST  /api/v1/portal/workbench/tasks/{taskId}/approve     同意
 *   POST  /api/v1/portal/workbench/tasks/{taskId}/reject      驳回
 *   POST  /api/v1/portal/workbench/tasks/{taskId}/transfer    转办
 *   POST  /api/v1/portal/workbench/tasks/batch-approve        批量同意
 *   POST  /api/v1/portal/workbench/tasks/batch-transfer       批量转办
 *
 * 详情 oa.portal.detail
 *   GET   /api/v1/portal/detail/{instanceId}                  详情头部
 *   GET   /api/v1/portal/detail/{instanceId}/form             表单分区与字段
 *   GET   /api/v1/portal/detail/{instanceId}/available-actions 当前用户可用动作
 *   GET   /api/v1/portal/detail/{instanceId}/trail            审批轨迹
 *   GET   /api/v1/portal/detail/{instanceId}/opinions         审批意见
 *   GET   /api/v1/portal/detail/{instanceId}/parallel-groups  并行协同分组
 *   POST  /api/v1/portal/detail/{instanceId}/actions/{action} 提交审批动作
 *
 * 三态读写与补件 oa.form / oa.workflow.supplement
 *   GET   /api/v1/forms/instances/{instanceId}/writable-fields 服务端可写字段白名单
 *   GET   /api/v1/forms/instances/{instanceId}/fields/{fieldId}/permission
 *   POST  /api/v1/flow/instances/{instanceId}/supplements      请求补件
 *   POST  /api/v1/flow/supplements/{supplementId}/submit       补件提交
 *   GET   /api/v1/flow/instances/{instanceId}/supplement-state 补件状态
 *   PUT   /api/v1/forms/seal/instances/{instanceId}/return-status 印鉴单归还状态（唯一例外）
 *   PUT   /api/v1/forms/seal/instances/{instanceId}/return-date   印鉴单归还日期（唯一例外）
 *
 * 打印 oa.form.print
 *   GET   /api/v1/portal/detail/{instanceId}/print-preview
 *   GET   /api/v1/forms/print/{instanceId}/visible-fields
 *   GET   /api/v1/forms/print/{instanceId}/signatures
 *   GET   /api/v1/forms/print/{instanceId}/pages
 */
import { get, post, put, withDemoFallback } from './http'
import {
  demoActionAvailability,
  demoDetailForm,
  demoDetailHeader,
  demoPrintDocument,
  demoSealReturnInfo,
  demoSupplementState,
  demoTrail,
  demoWorkbenchFilters,
  demoWorkbenchItems,
  demoWorkbenchSummary,
} from './demo'
import type {
  ActionAvailability,
  BatchApprovePayload,
  BatchTransferPayload,
  DetailAction,
  DetailForm,
  DetailHeader,
  FormType,
  PageResult,
  PrintDocument,
  SealReturnInfo,
  SupplementState,
  TaskDecisionPayload,
  TrailResult,
  WorkbenchFilterOption,
  WorkbenchItem,
  WorkbenchQuery,
  WorkbenchSummary,
} from '@/types/api'

// ---------------------------------------------------------------------------
// 工作台
// ---------------------------------------------------------------------------
export function queryWorkbench(query: WorkbenchQuery): Promise<PageResult<WorkbenchItem>> {
  return withDemoFallback(
    () => post<PageResult<WorkbenchItem>>('/portal/workbench/query', query),
    () => {
      const filtered = demoWorkbenchItems.filter((item) => {
        if (query.keyword && !`${item.title}${item.bizNo}${item.initiatorName}`.includes(query.keyword)) {
          return false
        }
        if (query.formTypes?.length && !query.formTypes.includes(item.formType)) return false
        if (query.statuses?.length && !query.statuses.includes(item.status)) return false
        return true
      })
      const start = (query.page - 1) * query.pageSize
      return {
        list: filtered.slice(start, start + query.pageSize),
        total: filtered.length,
        page: query.page,
        pageSize: query.pageSize,
      }
    },
  )
}

export function fetchWorkbenchSummary(): Promise<WorkbenchSummary> {
  return withDemoFallback(
    () => get<WorkbenchSummary>('/portal/workbench/summary'),
    () => demoWorkbenchSummary,
  )
}

export function fetchWorkbenchFilters(): Promise<WorkbenchFilterOption[]> {
  return withDemoFallback(
    () => get<WorkbenchFilterOption[]>('/portal/workbench/filters'),
    () => demoWorkbenchFilters,
  )
}

export function approveTask(taskId: string, payload: TaskDecisionPayload): Promise<void> {
  return post<void>(`/portal/workbench/tasks/${taskId}/approve`, payload)
}

export function rejectTask(taskId: string, payload: TaskDecisionPayload): Promise<void> {
  return post<void>(`/portal/workbench/tasks/${taskId}/reject`, payload)
}

export function transferTask(taskId: string, payload: TaskDecisionPayload): Promise<void> {
  return post<void>(`/portal/workbench/tasks/${taskId}/transfer`, payload)
}

/** 批量同意：逐条仍受节点状态与角色约束，服务端返回逐条结果 */
export function batchApprove(payload: BatchApprovePayload): Promise<{ succeeded: string[]; failed: Array<{ taskId: string; reason: string }> }> {
  return post('/portal/workbench/tasks/batch-approve', payload)
}

export function batchTransfer(payload: BatchTransferPayload): Promise<{ succeeded: string[]; failed: Array<{ taskId: string; reason: string }> }> {
  return post('/portal/workbench/tasks/batch-transfer', payload)
}

// ---------------------------------------------------------------------------
// 详情
// ---------------------------------------------------------------------------
export function fetchDetailHeader(instanceId: string): Promise<DetailHeader> {
  return withDemoFallback(
    () => get<DetailHeader>(`/portal/detail/${instanceId}`),
    () => demoDetailHeader,
  )
}

export function fetchDetailForm(instanceId: string): Promise<DetailForm> {
  return withDemoFallback(
    () => get<DetailForm>(`/portal/detail/${instanceId}/form`),
    () => demoDetailForm,
  )
}

export function fetchAvailableActions(instanceId: string): Promise<ActionAvailability[]> {
  return withDemoFallback(
    () => get<ActionAvailability[]>(`/portal/detail/${instanceId}/available-actions`),
    () => demoActionAvailability,
  )
}

export function fetchTrail(instanceId: string): Promise<TrailResult> {
  return withDemoFallback(() => get<TrailResult>(`/portal/detail/${instanceId}/trail`), () => demoTrail)
}

/** 提交审批动作：同意 / 驳回 / 流转 / 回退 / 终止 / 补件 */
export function submitDetailAction(
  instanceId: string,
  action: DetailAction,
  payload: TaskDecisionPayload,
): Promise<void> {
  return post<void>(`/portal/detail/${instanceId}/actions/${action}`, payload)
}

// ---------------------------------------------------------------------------
// 三态读写：可写字段白名单（服务端强制，前端只做渲染与提示）
// ---------------------------------------------------------------------------
export function fetchWritableFields(instanceId: string): Promise<string[]> {
  return withDemoFallback(
    () => get<string[]>(`/forms/instances/${instanceId}/writable-fields`),
    () => demoDetailForm.writableFieldIds,
  )
}

export function fetchSupplementState(instanceId: string): Promise<SupplementState> {
  return withDemoFallback(
    () => get<SupplementState>(`/flow/instances/${instanceId}/supplement-state`),
    () => demoSupplementState,
  )
}

/** 请求补件：同节点 ≤1 次、全单 ≤3 次，默认时限 3 个工作日 */
export function requestSupplement(
  instanceId: string,
  payload: { note: string; deadline?: string },
): Promise<void> {
  return post<void>(`/flow/instances/${instanceId}/supplements`, payload)
}

export function submitSupplement(
  supplementId: string,
  payload: { note: string; attachmentIds: string[] },
): Promise<void> {
  return post<void>(`/flow/supplements/${supplementId}/submit`, payload)
}

// ---------------------------------------------------------------------------
// 印鉴证照单归还信息：三态只读的唯一例外（发起人与节点⑦可改）
// ---------------------------------------------------------------------------
export function fetchSealReturnInfo(instanceId: string): Promise<SealReturnInfo> {
  return withDemoFallback(
    () => get<SealReturnInfo>(`/forms/instances/${instanceId}/fields/return-status`),
    () => demoSealReturnInfo,
  )
}

export function updateSealReturnStatus(instanceId: string, returnStatus: string): Promise<void> {
  return put<void>(`/forms/seal/instances/${instanceId}/return-status`, { returnStatus })
}

export function updateSealReturnDate(instanceId: string, returnDate: string): Promise<void> {
  return put<void>(`/forms/seal/instances/${instanceId}/return-date`, { returnDate })
}

// ---------------------------------------------------------------------------
// 打印（A4）
// ---------------------------------------------------------------------------
export function fetchPrintDocument(instanceId: string, formType: FormType): Promise<PrintDocument> {
  return withDemoFallback(
    () => get<PrintDocument>(`/forms/print/${printVariant(formType)}/${instanceId}`),
    () => ({ ...demoPrintDocument, instanceId }),
  )
}

/** 版式映射（DESIGN.md「版式来源」）：事项单统一走子公司内部审批单版式 */
function printVariant(formType: FormType): string {
  switch (formType) {
    case 'fund':
      return 'fund'
    case 'contract':
      return 'contract'
    case 'seal_cert':
      return 'seal'
    case 'matter':
    default:
      return 'internal'
  }
}
