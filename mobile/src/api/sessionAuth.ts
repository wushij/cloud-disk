/** 与桌面端一致：Bearer 仅存 sessionStorage（H5），关闭标签页即失效，不写 localStorage */
const SESSION_KEY = 'cd_session_bearer'
const LEGACY_KEY = 'cd_token'

let sessionBearer: string | null = null

function readSessionStorage(): string | null {
  try {
    return sessionStorage.getItem(SESSION_KEY)
  } catch {
    return null
  }
}

function writeSessionStorage(token: string | null) {
  try {
    if (token) sessionStorage.setItem(SESSION_KEY, token)
    else sessionStorage.removeItem(SESSION_KEY)
  } catch {
    /* 隐私模式等环境可能不可用 */
  }
}

export function setSessionBearer(token: string | null) {
  sessionBearer = token?.trim() || null
  // #ifdef H5
  writeSessionStorage(sessionBearer)
  // #endif
  // #ifndef H5
  if (sessionBearer) uni.setStorageSync(SESSION_KEY, sessionBearer)
  else uni.removeStorageSync(SESSION_KEY)
  // #endif
}

export function getSessionBearer(): string | null {
  if (sessionBearer) return sessionBearer
  // #ifdef H5
  sessionBearer = readSessionStorage()
  // #endif
  // #ifndef H5
  sessionBearer = uni.getStorageSync(SESSION_KEY) || null
  // #endif
  return sessionBearer
}

/** 一次性迁移旧版 cd_token（曾存 localStorage） */
export function migrateLegacySessionToken(): string | null {
  let legacy: string | null = null
  try {
    legacy = uni.getStorageSync(LEGACY_KEY) || null
  } catch {
    /* ignore */
  }
  if (!legacy) {
    try {
      legacy = typeof localStorage !== 'undefined' ? localStorage.getItem(LEGACY_KEY) : null
    } catch {
      /* ignore */
    }
  }
  if (legacy?.trim()) {
    setSessionBearer(legacy.trim())
    clearLegacyToken()
    return legacy.trim()
  }
  return null
}

export function clearLegacyToken() {
  try {
    uni.removeStorageSync(LEGACY_KEY)
  } catch {
    /* ignore */
  }
  try {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(LEGACY_KEY)
  } catch {
    /* ignore */
  }
}
