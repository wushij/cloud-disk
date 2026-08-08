import http from './http'
import type { UserVO } from './types'

export const userApi = {
  // 获取个人基本信息
  profile: () => http.get<UserVO>('/api/auth/me'),

  // 更新个人资料
  updateProfile: (body: {
    nickname?: string
    phone?: string
    email?: string
    emailCode?: string
  }) => http.put<UserVO>('/api/auth/profile', body),

  // 上传头像
  uploadAvatar: (formData: FormData) =>
    http.post<UserVO>('/api/auth/avatar', formData),

  // 头像预览 URL 构造
  avatarViewUrl: () => '/api/auth/avatar/view',
}
