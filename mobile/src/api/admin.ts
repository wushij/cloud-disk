import { request } from './http'
import type { UserRow, MessageVO } from './types'

export const adminApi = {
  // 控制台看板统计数据
  dashboard: () => request<Record<string, unknown>>({ url: '/api/admin/dashboard' }),

  // 用户列表
  users: () => request<UserRow[]>({ url: '/api/admin/users' }),

  // 用户头像 URL 构造
  userAvatarUrl: (id: number) => `/api/admin/users/${id}/avatar`,

  // 修改用户状态（启用/禁用）
  setUserStatus: (id: number, status: number) =>
    request<MessageVO>({ url: `/api/admin/users/${id}/status`, method: 'PUT', data: { status } }),

  // 修改用户角色
  setUserRole: (id: number, role: string) =>
    request<Record<string, unknown>>({ url: `/api/admin/users/${id}/role`, method: 'PUT', data: { role } }),

  // 设置用户配额
  setUserQuota: (id: number, storageQuota: number) =>
    request<MessageVO>({ url: `/api/admin/users/${id}/quota`, method: 'PUT', data: { storageQuota } }),
  setQuota: (id: number, storageQuota: number) =>
    request<MessageVO>({ url: `/api/admin/users/${id}/quota`, method: 'PUT', data: { storageQuota } }),

  // 重置用户密码
  resetPassword: (id: number, password: string) =>
    request<MessageVO>({ url: `/api/admin/users/${id}/password`, method: 'PUT', data: { password } }),
  resetUserPassword: (id: number, password: string) =>
    request<MessageVO>({ url: `/api/admin/users/${id}/password`, method: 'PUT', data: { password } }),

  // 注册审批：通过
  approveRegistration: (userId: number) =>
    request<MessageVO>({ url: `/api/admin/registrations/${userId}/approve`, method: 'POST' }),

  // 注册审批：拒绝
  rejectRegistration: (userId: number) =>
    request<MessageVO>({ url: `/api/admin/registrations/${userId}/reject`, method: 'POST' }),

  // 重建搜索索引
  rebuildSearch: () => request<MessageVO>({ url: '/api/admin/search/rebuild', method: 'POST' }),

  // 审计日志
  auditLogs: (page = 0, size = 20) =>
    request<Record<string, unknown>>({ url: '/api/admin/audit-logs', data: { page, size } }),

  // 存储统计
  storageStats: () => request<Record<string, unknown>>({ url: '/api/admin/storage/stats' }),

  // 获取系统安全配置
  getSecurityConfig: () => request<Record<string, unknown>>({ url: '/api/admin/security/config' }),
  securityConfig: () => request<Record<string, unknown>>({ url: '/api/admin/security/config' }),

  // 热更新系统安全配置
  updateSecurityConfig: (body: Record<string, unknown>) =>
    request<Record<string, unknown>>({ url: '/api/admin/security/config', method: 'PUT', data: body }),
  saveSecurityConfig: (body: Record<string, unknown>) =>
    request<Record<string, unknown>>({ url: '/api/admin/security/config', method: 'PUT', data: body }),
}
