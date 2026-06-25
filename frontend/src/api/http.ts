import axios from 'axios'
import { getApiErrorMessage, shouldShowGlobalError, showErrorToast } from '@/utils/error'

const TOKEN_KEY = 'cd_token'
const USER_KEY = 'cd_username'
const NICKNAME_KEY = 'cd_nickname'
const ROLE_KEY = 'cd_role'
const AVATAR_VERSION_KEY = 'cd_avatar_v'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 0
})

function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(NICKNAME_KEY)
  localStorage.removeItem(ROLE_KEY)
}

function redirectToLogin() {
  const path = window.location.pathname
  if (!path.startsWith('/login') && !path.startsWith('/share')) {
    window.location.href = '/login'
  }
}

http.interceptors.request.use((config) => {
  const path = config.url ?? ''
  const isAuth = path.includes('/api/auth/login') || path.includes('/api/auth/register')
  if (isAuth) {
    delete config.headers.Authorization
    config.skipErrorHandler = true
    return config
  }
  const t = localStorage.getItem(TOKEN_KEY)
  if (t) config.headers.Authorization = `Bearer ${t}`
  return config
})

function isPublicAuthRequest(url: string) {
  return /\/api\/auth\/(login|register|captcha|providers|ldap\/login|sso)/.test(url)
}

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const url = error.config?.url ?? ''
      const onLoginPage = window.location.pathname.startsWith('/login')
      const isPublic = isPublicAuthRequest(url) || error.config?.skipErrorHandler

      if (!isPublic && shouldShowGlobalError(error)) {
        showErrorToast(getApiErrorMessage(error, '未登录或登录已过期，请重新登录'))
      }
      if (!isPublic && !onLoginPage) {
        clearAuth()
        redirectToLogin()
      }
    } else if (shouldShowGlobalError(error)) {
      showErrorToast(getApiErrorMessage(error))
    }
    return Promise.reject(error)
  }
)

const HAS_AVATAR_KEY = 'cd_has_avatar'

export { TOKEN_KEY, USER_KEY, NICKNAME_KEY, ROLE_KEY, AVATAR_VERSION_KEY, HAS_AVATAR_KEY }
export default http
