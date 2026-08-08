import http from './http'
import type { UserRow, MessageVO } from './types'

export const adminApi = {
  // 控制台看板统计数据
  dashboard: () => http.get<Record<string, unknown>>('/api/admin/dashboard'),

  // 用户列表
  users: () => http.get<UserRow[]>('/api/admin/users'),

  // 用户头像查看 URL 构造器
  userAvatarUrl: (id: number) => `/api/admin/users/${id}/avatar`,

  // 修改用户状态（启用/禁用）
  setUserStatus: (id: number, status: number) =>
    http.put<MessageVO>(`/api/admin/users/${id}/status`, { status }),

  // 修改用户角色
  setUserRole: (id: number, role: string) =>
    http.put<Record<string, unknown>>(`/api/admin/users/${id}/role`, { role }),

  // 设置用户配额
  setUserQuota: (id: number, storageQuota: number) =>
    http.put<MessageVO>(`/api/admin/users/${id}/quota`, { storageQuota }),

  // 重置用户密码
  resetPassword: (id: number, password: string) =>
    http.put<MessageVO>(`/api/admin/users/${id}/password`, { password }),

  // 注册审批：通过
  approveRegistration: (userId: number) =>
    http.post<MessageVO>(`/api/admin/registrations/${userId}/approve`),

  // 注册审批：拒绝
  rejectRegistration: (userId: number) =>
    http.post<MessageVO>(`/api/admin/registrations/${userId}/reject`),

  // 重建搜索索引
  rebuildSearch: () => http.post<MessageVO>('/api/admin/search/rebuild'),

  // 审计日志
  auditLogs: (page = 0, size = 20) =>
    http.get<Record<string, unknown>>('/api/admin/audit-logs', { params: { page, size } }),

  // 存储统计
  storageStats: () => http.get<Record<string, unknown>>('/api/admin/storage/stats'),

  // 获取系统安全配置
  getSecurityConfig: () => http.get<Record<string, unknown>>('/api/admin/security/config'),

  // 热更新系统安全配置
  updateSecurityConfig: (body: Record<string, unknown>) =>
    http.put<Record<string, unknown>>('/api/admin/security/config', body),
}
