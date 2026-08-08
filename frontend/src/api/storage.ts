import http from './http'
import type { StorageUsageVO, QuotaApplicationVO, MessageVO } from './types'

export const storageApi = {
  // 基础存储服务信息
  info: () => http.get<Record<string, unknown>>('/api/storage/info'),

  // 当前登录用户的存储空间使用量
  usage: () => http.get<StorageUsageVO>('/api/storage/usage'),

  // 提交存储配额扩容申请
  applyQuota: (body: { applyQuota: number; reason?: string }) =>
    http.post<{ message: string; id: number }>('/api/quota-applications', body),

  // 我的配额扩容申请历史
  myQuotaHistory: () => http.get<QuotaApplicationVO[]>('/api/quota-applications/my'),

  // 管理员：获取全站配额扩容申请列表
  adminQuotaApplications: (params?: { page?: number; size?: number }) =>
    http.get<{ content: QuotaApplicationVO[]; totalElements: number }>('/api/quota-applications/admin', { params }),

  // 管理员：审批通过
  approveQuota: (id: number, opinion?: string) =>
    http.post<MessageVO>(`/api/quota-applications/admin/${id}/approve`, { opinion }),

  // 管理员：审批拒绝
  rejectQuota: (id: number, opinion?: string) =>
    http.post<MessageVO>(`/api/quota-applications/admin/${id}/reject`, { opinion }),
}
