import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import http, { TOKEN_KEY, USER_KEY, NICKNAME_KEY, ROLE_KEY, AVATAR_VERSION_KEY, HAS_AVATAR_KEY } from '@/api/http'
import { clearMediaTokenCache, ensureMediaToken, refreshMediaToken } from '@/utils/mediaToken'
import { mediaApiUrl } from '@/utils/mediaUrl'
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

  const effectiveAvatarVersion = computed(() => {
    const fromPath = avatarVersionFromPath(avatarStoragePath.value)
    if (fromPath) return fromPath
    return avatarVersion.value
  })

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN' || role.value === 'SUPER_ADMIN')
  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')

  const avatarSrc = computed(() => {
    if (!hasAvatar.value) return ''
    const v = effectiveAvatarVersion.value
    return `${mediaApiUrl('/api/auth/avatar/view')}?v=${v}`
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
    avatarCachedSrc.value = ''
    if (!hasAvatar.value || !username.value) return
    avatarCachedSrc.value = loadAvatarThumb(username.value, effectiveAvatarVersion.value)
  }

  function restore() {
    token.value = localStorage.getItem(TOKEN_KEY)
    username.value = localStorage.getItem(USER_KEY)
    nickname.value = localStorage.getItem(NICKNAME_KEY)
    role.value = localStorage.getItem(ROLE_KEY)
    avatarVersion.value = Number(localStorage.getItem(AVATAR_VERSION_KEY) || '0')
    hasAvatar.value = localStorage.getItem(HAS_AVATAR_KEY) === 'true'
    restoreAvatarCache()
    if (token.value) {
      void ensureMediaToken()
    }
  }

  async function initAuth() {
    restore()
    if (!token.value) return
    await Promise.all([
      ensureMediaToken(),
      http.post('/api/auth/sync-cookie', null, { skipErrorHandler: true }).catch(() => {})
    ])
    await fetchProfile().catch(() => {})
  }

  function persist() {
    if (token.value) localStorage.setItem(TOKEN_KEY, token.value)
    else localStorage.removeItem(TOKEN_KEY)
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
  }) {
    if (data.id != null) userId.value = data.id
    if (data.username) username.value = data.username
    nickname.value = data.nickname || data.username || nickname.value
    role.value = data.role || role.value || 'USER'
    avatarStoragePath.value = data.avatar || ''
    hasAvatar.value = !!data.avatar
    if (!data.avatar) {
      avatarCachedSrc.value = ''
      clearAvatarThumb()
    } else {
      restoreAvatarCache()
    }
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

  async function login(u: string, p: string, captcha?: { captchaId?: string; captchaAnswer?: string }) {
    avatarCachedSrc.value = ''
    avatarStoragePath.value = ''
    const { data } = await http.post('/api/auth/login', { username: u, password: p, ...captcha }, { skipErrorHandler: true })
    token.value = data.token
    username.value = data.username
    nickname.value = data.nickname || data.username
    role.value = data.role || 'USER'
    persist()
    await fetchProfile()
    await refreshMediaToken()
    await http.post('/api/auth/sync-cookie', null, { skipErrorHandler: true }).catch(() => {})
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
    token.value = data.token
    username.value = data.username
    nickname.value = data.nickname || data.username
    role.value = data.role || 'USER'
    persist()
    await fetchProfile()
    await refreshMediaToken()
    await http.post('/api/auth/sync-cookie', null, { skipErrorHandler: true }).catch(() => {})
    return data
  }

  async function fetchProfile() {
    const { data } = await http.get('/api/auth/me')
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

  function logout() {
    token.value = null
    userId.value = null
    username.value = null
    nickname.value = null
    role.value = null
    hasAvatar.value = false
    avatarVersion.value = 0
    avatarStoragePath.value = ''
    avatarCachedSrc.value = ''
    localStorage.removeItem(AVATAR_VERSION_KEY)
    localStorage.removeItem(HAS_AVATAR_KEY)
    clearAvatarThumb()
    clearMediaTokenCache()
    persist()
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
    restore,
    initAuth,
    restoreAvatarCache,
    bumpAvatar,
    login,
    register,
    fetchProfile,
    updateProfile,
    uploadAvatar,
    logout,
    refreshMediaToken,
    ensureMediaToken
  }
})
