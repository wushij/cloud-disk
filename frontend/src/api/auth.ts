import http from './http'
import type {
  AuthTokenVO,
  CaptchaVO,
  CaptchaRequiredVO,
  RegisterResultVO,
  SecurityPublicConfigVO,
  UserVO,
  OperationResultVO,
  MessageVO
} from './types'

export const authApi = {
  // 获取公钥配置
  config: () => http.get<SecurityPublicConfigVO>('/api/auth/config'),

  // 验证码与安全性
  captcha: () => http.get<CaptchaVO>('/api/auth/captcha'),
  captchaRequired: () => http.get<CaptchaRequiredVO>('/api/auth/captcha/required'),

  // 密码登录
  login: (body: {
    username: string
    password: string
    captchaId?: string
    captchaAnswer?: string
  }) => http.post<AuthTokenVO>('/api/auth/login', body),

  // LDAP 登录
  ldapLogin: (body: {
    username: string
    password: string
    captchaId?: string
    captchaAnswer?: string
  }) => http.post<Record<string, unknown>>('/api/auth/ldap/login', body),

  // 邮箱验证码
  sendEmailCode: (body: { email: string; scene: string }) =>
    http.post<OperationResultVO>('/api/auth/email/send-code', body),

  // 账号登录 Provider 查询
  providers: () =>
    http.get<{
      ldapEnabled?: boolean
      ssoEnabled?: boolean
      sso?: { authorizeUrl?: string; providerName?: string }
    }>('/api/auth/providers'),

  // SSO Ticket 验证
  ssoTicket: (ticket: string) =>
    http.post<AuthTokenVO>('/api/auth/sso/ticket', { ticket }),

  // 邮箱登录
  emailLogin: (body: { email: string; code: string }) =>
    http.post<AuthTokenVO>('/api/auth/email/login', body),

  // 邮箱重置密码
  resetPasswordByEmail: (body: { email: string; code: string; newPassword: string }) =>
    http.post<OperationResultVO>('/api/auth/email/reset-password', body),

  // 注册
  register: (body: Record<string, unknown>) =>
    http.post<RegisterResultVO>('/api/auth/register', body),

  // 获取当前登录用户信息
  me: () => http.get<UserVO>('/api/auth/me'),

  // 获取 WebSocket 凭证
  wsTicket: () => http.post<{ ticket: string }>('/api/auth/ws-ticket'),

  // 获取媒体预览 AccessToken
  mediaToken: () => http.get<Record<string, unknown>>('/api/auth/media-token'),

  // 修改个人资料
  updateProfile: (body: {
    nickname?: string
    phone?: string
    email?: string
    emailCode?: string
  }) => http.put<UserVO>('/api/auth/profile', body),

  // 上传头像
  uploadAvatar: (formData: FormData) =>
    http.post<UserVO>('/api/auth/avatar', formData),

  // 登出
  logout: () => http.post<MessageVO>('/api/auth/logout'),

  // 同步 Cookie 凭证
  syncCookie: () => http.post<MessageVO>('/api/auth/sync-cookie'),
}
