import { request } from './http'
import type { UserVO } from './types'

export const userApi = {
  // 获取个人资料
  profile: () => request<UserVO>({ url: '/api/auth/me' }),

  // 更新个人资料
  updateProfile: (body: { nickname?: string; phone?: string; email?: string; emailCode?: string }) =>
    request<UserVO>({ url: '/api/auth/profile', method: 'PUT', data: body }),

  // 接口 URL 构造
  avatarUrl: (userId: number) => `/api/users/${userId}/avatar`,
}
