/** 当前标签页会话 Token：sessionStorage 仅在同标签刷新后保留，不写 localStorage */
const SESSION_KEY = 'cd_session_bearer'

let sessionBearer: string | null = null

export function setSessionBearer(token: string | null) {
  sessionBearer = token?.trim() || null
  try {
    if (sessionBearer) sessionStorage.setItem(SESSION_KEY, sessionBearer)
    else sessionStorage.removeItem(SESSION_KEY)
  } catch {
    /* 隐私模式等环境可能不可用 */
  }
}

export function getSessionBearer(): string | null {
  if (sessionBearer) return sessionBearer
  try {
    sessionBearer = sessionStorage.getItem(SESSION_KEY)
  } catch {
    sessionBearer = null
  }
  return sessionBearer
}
