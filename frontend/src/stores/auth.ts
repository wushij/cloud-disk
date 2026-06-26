import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import axios from 'axios'
import http, { USER_KEY, NICKNAME_KEY, ROLE_KEY, AVATAR_VERSION_KEY, HAS_AVATAR_KEY } from '@/api/http'
import { setSessionBearer, getSessionBearer } from '@/api/sessionAuth'
import { clearMediaTokenCache, ensureMediaToken, refreshMediaToken, mediaTokenRef } from '@/utils/mediaToken'
import { mediaApiUrl, appendQueryParam } from '@/utils/mediaUrl'
import {
  loadAvatarThumb,
  clearAvatarThumb,
  cacheAvatarFromFile,
  cacheAvatarFromUrl,
  avatarVersionFromPath
} from '@/utils/avatarCache'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const userId = ref<number | null>(null)
  const username = ref<string | null>(null)
  const nickname = ref<string | null>(null)
  const role = ref<string | null>(null)
  const hasAvatar = ref(false)
  const avatarVersion = ref(0)
  const avatarStoragePath = ref('')
  const avatarCachedSrc = ref('')
  const defaultPassword = ref(false)

  const effectiveAvatarVersion = computed(() => {
    const fromPath = avatarVersionFromPath(avatarStoragePath.value)
    if (fromPath) return fromPath
    return avatarVersion.value
  })

  const isLoggedIn = computed(() => !!username.value)
  const isAdmin = computed(() => role.value === 'ADMIN' || role.value === 'SUPER_ADMIN')
  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')

  const avatarSrc = computed(() => {
    if (!hasAvatar.value || !mediaTokenRef.value) return ''
    const v = effectiveAvatarVersion.value
    return appendQueryParam(mediaApiUrl('/api/auth/avatar/view'), 'v', v)
  })

  const avatarDisplaySrc = computed(() => {
    if (!hasAvatar.value) return ''
    if (avatarSrc.value) return avatarSrc.value
    return avatarCachedSrc.value
  })

  const avatarInitial = computed(() =>
    (nickname.value || username.value || 'U').charAt(0).toUpperCase()
  )

  const avatarReady = computed(() => hasAvatar.value && !!avatarSrc.value)

  function restoreAvatarCache() {
    if (!hasAvatar.value || !username.value) {
      avatarCachedSrc.value = ''
      return
    }
    const cached = loadAvatarThumb(username.value, effectiveAvatarVersion.value)
    if (cached) {
      avatarCachedSrc.value = cached
    }
  }

  function restore() {
    getSessionBearer()
    // 仅冷启动时从 localStorage 恢复；路由切换时保留 Pinia 内头像等会话状态
    if (!username.value) {
      username.value = localStorage.getItem(USER_KEY)
      nickname.value = localStorage.getItem(NICKNAME_KEY)
      role.value = localStorage.getItem(ROLE_KEY)
      avatarVersion.value = Number(localStorage.getItem(AVATAR_VERSION_KEY) || '0')
    }
    if (hasAvatar.value) {
      restoreAvatarCache()
    }
  }

  function isUnauthorizedError(e: unknown): boolean {
    return axios.isAxiosError(e) && e.response?.status === 401
  }

  async function syncSessionCookie() {
    try {
      await http.post('/api/auth/sync-cookie', undefined, { skipErrorHandler: true })
    } catch {
      /* Cookie 同步失败时仍可用 Bearer 完成会话 */
    }
  }

  async function initAuth() {
    restore()
    const path = window.location.pathname
    const hasSsoTicket = new URLSearchParams(window.location.search).has('sso_ticket')

    if (path.startsWith('/login') || path.startsWith('/share')) {
      if (path.startsWith('/login') && !hasSsoTicket) {
        clearClientState()
      }
      return
    }

    try {
      await fetchProfile({ silent: true })
    } catch (e) {
      if (isUnauthorizedError(e)) {
        clearClientState()
      }
      return
    }

    await syncSessionCookie()
    try {
      await ensureMediaToken()
    } catch {
      /* 媒体 token 失败不踢出登录 */
    }
  }

  function clearClientState() {
    setSessionBearer(null)
    token.value = null
    userId.value = null
    username.value = null
    nickname.value = null
    role.value = null
    hasAvatar.value = false
    avatarVersion.value = 0
    avatarStoragePath.value = ''
    avatarCachedSrc.value = ''
    defaultPassword.value = false
    localStorage.removeItem(AVATAR_VERSION_KEY)
    localStorage.removeItem(HAS_AVATAR_KEY)
    clearAvatarThumb()
    clearMediaTokenCache()
    persist()
  }

  function persist() {
    // Rely on HttpOnly Cookie. Do not persist token to localStorage
    if (username.value) localStorage.setItem(USER_KEY, username.value)
    else localStorage.removeItem(USER_KEY)
    if (nickname.value) localStorage.setItem(NICKNAME_KEY, nickname.value)
    else localStorage.removeItem(NICKNAME_KEY)
    if (role.value) localStorage.setItem(ROLE_KEY, role.value)
    else localStorage.removeItem(ROLE_KEY)
    localStorage.setItem(HAS_AVATAR_KEY, String(hasAvatar.value))
  }

  function applyProfile(data: {
    id?: number
    username?: string
    nickname?: string
    role?: string
    avatar?: string | null
    defaultPassword?: boolean
  }) {
    if (data.id != null) userId.value = data.id
    if (data.username) username.value = data.username
    nickname.value = data.nickname || data.username || nickname.value
    role.value = data.role || role.value || 'USER'
    avatarStoragePath.value = data.avatar || ''
    hasAvatar.value = !!data.avatar
    if (data.defaultPassword !== undefined) defaultPassword.value = data.defaultPassword
    if (!data.avatar) {
      avatarCachedSrc.value = ''
      clearAvatarThumb()
    } else {
      restoreAvatarCache()
    }
    persist()
  }

  function markAvatarUnavailable() {
    if (!hasAvatar.value) return
    hasAvatar.value = false
    avatarStoragePath.value = ''
    avatarCachedSrc.value = ''
    clearAvatarThumb()
    persist()
  }

  function bumpAvatar() {
    avatarVersion.value++
    localStorage.setItem(AVATAR_VERSION_KEY, String(avatarVersion.value))
    hasAvatar.value = true
    persist()
  }

  watch(
    avatarSrc,
    (url) => {
      if (!url || !username.value) return
      void cacheAvatarFromUrl(username.value, effectiveAvatarVersion.value, url)
        .then((data) => {
          avatarCachedSrc.value = data
        })
        .catch(() => {})
    },
    { immediate: true }
  )

  async function establishSession(tokenValue: string) {
    setSessionBearer(tokenValue)
    token.value = tokenValue
    await syncSessionCookie()
  }

  async function login(u: string, p: string, captcha?: { captchaId?: string; captchaAnswer?: string }) {
    avatarCachedSrc.value = ''
    avatarStoragePath.value = ''
    const { data } = await http.post('/api/auth/login', { username: u, password: p, ...captcha }, { skipErrorHandler: true })
    await establishSession(data.token)
    username.value = data.username
    nickname.value = data.nickname || data.username
    role.value = data.role || 'USER'
    defaultPassword.value = data.defaultPassword || false
    persist()
    await fetchProfile({ silent: true })
    await refreshMediaToken()
  }

  async function ldapLogin(
    u: string,
    p: string,
    captcha?: { captchaId?: string; captchaAnswer?: string }
  ) {
    avatarCachedSrc.value = ''
    avatarStoragePath.value = ''
    const { data } = await http.post(
      '/api/auth/ldap/login',
      { username: u, password: p, ...captcha },
      { skipErrorHandler: true }
    )
    await establishSession(data.token)
    username.value = data.username
    nickname.value = data.nickname || data.username
    role.value = data.role || 'USER'
    defaultPassword.value = data.defaultPassword || false
    persist()
    await fetchProfile({ silent: true })
    await refreshMediaToken()
  }

  async function register(
    u: string,
    p: string,
    nick?: string,
    captcha?: { captchaId?: string; captchaAnswer?: string }
  ) {
    const { data } = await http.post(
      '/api/auth/register',
      { username: u, password: p, nickname: nick, ...captcha },
      { skipErrorHandler: true }
    )
    if (data.pending) {
      return data as { pending: true; title?: string; message?: string }
    }
    await establishSession(data.token)
    username.value = data.username
    nickname.value = data.nickname || data.username
    role.value = data.role || 'USER'
    defaultPassword.value = data.defaultPassword || false
    persist()
    await fetchProfile({ silent: true })
    await refreshMediaToken()
    return data
  }

  async function fetchProfile(opts?: { silent?: boolean }) {
    const { data } = await http.get('/api/auth/me', opts?.silent ? { skipErrorHandler: true } : undefined)
    applyProfile(data)
    return data
  }

  async function updateProfile(payload: { nickname?: string; email?: string; phone?: string }) {
    const { data } = await http.put('/api/auth/profile', payload)
    applyProfile(data)
    return data
  }

  async function uploadAvatar(file: File) {
    const u = username.value
    const fd = new FormData()
    fd.append('file', file)
    const { data } = await http.post('/api/auth/avatar', fd)
    bumpAvatar()
    applyProfile(data)
    if (u) {
      try {
        avatarCachedSrc.value = await cacheAvatarFromFile(u, effectiveAvatarVersion.value, file)
      } catch {
        /* ignore */
      }
    }
    await ensureMediaToken()
    return data
  }

  async function logout() {
    try {
      await http.post('/api/auth/logout', undefined, { skipErrorHandler: true })
    } catch {
      /* ignore */
    }
    clearClientState()
  }

  async function completeSsoSession(data: {
    token: string
    username?: string
    nickname?: string
    role?: string
  }) {
    await establishSession(data.token)
    username.value = data.username || null
    nickname.value = data.nickname || data.username || null
    role.value = data.role || 'USER'
    persist()
    await fetchProfile({ silent: true })
    await refreshMediaToken()
  }

  return {
    token,
    userId,
    username,
    nickname,
    role,
    hasAvatar,
    avatarVersion,
    avatarSrc,
    avatarDisplaySrc,
    avatarCachedSrc,
    avatarReady,
    avatarInitial,
    isLoggedIn,
    isAdmin,
    isSuperAdmin,
    defaultPassword,
    restore,
    initAuth,
    restoreAvatarCache,
    bumpAvatar,
    login,
    ldapLogin,
    completeSsoSession,
    register,
    fetchProfile,
    updateProfile,
    uploadAvatar,
    logout,
    refreshMediaToken,
    ensureMediaToken,
    markAvatarUnavailable
  }
})
