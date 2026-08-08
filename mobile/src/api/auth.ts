import { request } from './http'
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
  // 密码登录
  login: (body: { username: string; password: string; captchaId?: string; captchaAnswer?: string }) =>
    request<AuthTokenVO>({ url: '/api/auth/login', method: 'POST', data: body }),

  // LDAP 登录
  ldapLogin: (body: { username: string; password: string; captchaId?: string; captchaAnswer?: string }) =>
    request<Record<string, unknown>>({ url: '/api/auth/ldap/login', method: 'POST', data: body }),

  // 注册
  register: (body: Record<string, unknown>) =>
    request<RegisterResultVO>({ url: '/api/auth/register', method: 'POST', data: body }),

  // 图形验证码
  captcha: () => request<CaptchaVO>({ url: '/api/auth/captcha' }),

  // 是否需要图形验证码
  captchaRequired: () => request<CaptchaRequiredVO>({ url: '/api/auth/captcha/required' }),

  // 系统安全配置
  securityConfig: () => request<SecurityPublicConfigVO>({ url: '/api/auth/config' }),

  // 账号登录 Provider 查询
  providers: () =>
    request<{
      ldapEnabled?: boolean
      ssoEnabled?: boolean
      sso?: { authorizeUrl?: string; providerName?: string }
    }>({ url: '/api/auth/providers' }),

  // SSO Ticket 验证
  ssoTicket: (ticket: string) =>
    request<AuthTokenVO>({ url: '/api/auth/sso/ticket', method: 'POST', data: { ticket } }),

  // 邮箱验证码发送
  sendEmailCode: (body: { email: string; scene: string }) =>
    request<OperationResultVO>({ url: '/api/auth/email/send-code', method: 'POST', data: body }),

  // 邮箱快捷登录
  emailLogin: (body: { email: string; code: string }) =>
    request<AuthTokenVO>({ url: '/api/auth/email/login', method: 'POST', data: body }),

  // 邮箱重置密码
  resetPasswordByEmail: (body: { email: string; code: string; newPassword: string }) =>
    request<OperationResultVO>({ url: '/api/auth/email/reset-password', method: 'POST', data: body }),

  // 获取当前登录用户
  me: () => request<UserVO>({ url: '/api/auth/me' }),

  // 获取 WebSocket 凭证
  wsTicket: () => request<{ ticket: string }>({ url: '/api/auth/ws-ticket', method: 'POST' }),

  // 获取媒体预览 Token
  mediaToken: () => request<{ mediaToken: string; expiresIn: number }>({ url: '/api/auth/media-token' }),

  // 更新个人资料
  updateProfile: (body: { nickname?: string; phone?: string; email?: string; emailCode?: string }) =>
    request<UserVO>({ url: '/api/auth/profile', method: 'PUT', data: body }),

  // 登出
  logout: () => request<MessageVO>({ url: '/api/auth/logout', method: 'POST' }),

  // 同步 Cookie 凭证
  syncCookie: () => request<MessageVO>({ url: '/api/auth/sync-cookie', method: 'POST' }),

  // 协商会话签名密钥
  sessionSignInit: (clientId: string) =>
    request<any>({ url: `/api/auth/session-sign-init?clientId=${clientId}`, method: 'POST', skipErrorHandler: true }),
}
