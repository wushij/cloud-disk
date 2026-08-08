import { request } from './http'
import type { StorageUsageVO, QuotaApplicationVO, MessageVO } from './types'

export const storageApi = {
  // 获取存储配置基础信息
  info: () => request<Record<string, unknown>>({ url: '/api/storage/info' }),

  // 获取用户实时存储容量与配额
  usage: () => request<StorageUsageVO>({ url: '/api/storage/usage' }),

  // 申请扩容
  applyQuota: (body: { applyQuota: number; reason?: string }) =>
    request<{ message: string; id: number }>({ url: '/api/quota-applications', method: 'POST', data: body }),

  // 查看我的扩容历史
  myQuotaHistory: () => request<QuotaApplicationVO[]>({ url: '/api/quota-applications/my' }),

  // 管理员查看扩容申请列表
  adminQuotaApplications: (params?: { page?: number; size?: number; status?: string }) =>
    request<{ content: QuotaApplicationVO[]; totalElements: number }>({
      url: '/api/quota-applications/admin',
      data: params
    }),

  // 管理员审批通过扩容
  approveQuota: (id: number, opinion?: string) =>
    request<MessageVO>({ url: `/api/quota-applications/admin/${id}/approve`, method: 'POST', data: { opinion } }),

  // 管理员拒绝扩容
  rejectQuota: (id: number, opinion?: string) =>
    request<MessageVO>({ url: `/api/quota-applications/admin/${id}/reject`, method: 'POST', data: { opinion } }),
}
