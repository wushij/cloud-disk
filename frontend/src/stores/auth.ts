import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import http, { TOKEN_KEY, USER_KEY, NICKNAME_KEY, ROLE_KEY, AVATAR_VERSION_KEY } from '@/api/http'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const username = ref<string | null>(null)
  const nickname = ref<string | null>(null)
  const role = ref<string | null>(null)
  const hasAvatar = ref(false)
  const avatarVersion = ref(0)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN' || role.value === 'SUPER_ADMIN')
  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')

  const avatarSrc = computed(() => {
    const t = token.value || localStorage.getItem(TOKEN_KEY)
    if (!t) return ''
    const base = import.meta.env.VITE_API_BASE || ''
    return `${base}/api/auth/avatar/view?access_token=${encodeURIComponent(t)}&v=${avatarVersion.value}`
  })

  const avatarInitial = computed(() =>
    (nickname.value || username.value || 'U').charAt(0).toUpperCase()
  )

  function restore() {
    token.value = localStorage.getItem(TOKEN_KEY)
    username.value = localStorage.getItem(USER_KEY)
    nickname.value = localStorage.getItem(NICKNAME_KEY)
    role.value = localStorage.getItem(ROLE_KEY)
    avatarVersion.value = Number(localStorage.getItem(AVATAR_VERSION_KEY) || '0')
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
  }

  function applyProfile(data: { username?: string; nickname?: string; role?: string; avatar?: string | null }) {
    if (data.username) username.value = data.username
    nickname.value = data.nickname || data.username || nickname.value
    role.value = data.role || role.value || 'USER'
    hasAvatar.value = !!data.avatar
    persist()
  }

  function bumpAvatar() {
    avatarVersion.value++
    localStorage.setItem(AVATAR_VERSION_KEY, String(avatarVersion.value))
    hasAvatar.value = true
  }

  async function login(u: string, p: string, captcha?: { captchaId?: string; captchaAnswer?: string }) {
    const { data } = await http.post('/api/auth/login', { username: u, password: p, ...captcha }, { skipErrorHandler: true })
    token.value = data.token
    username.value = data.username
    nickname.value = data.nickname || data.username
    role.value = data.role || 'USER'
    persist()
    await fetchProfile()
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
    const fd = new FormData()
    fd.append('file', file)
    await http.post('/api/auth/avatar', fd)
    bumpAvatar()
    const data = await fetchProfile()
    return data
  }

  function logout() {
    token.value = null
    username.value = null
    nickname.value = null
    role.value = null
    hasAvatar.value = false
    avatarVersion.value = 0
    localStorage.removeItem(AVATAR_VERSION_KEY)
    persist()
  }

  return {
    token,
    username,
    nickname,
    role,
    hasAvatar,
    avatarVersion,
    avatarSrc,
    avatarInitial,
    isLoggedIn,
    isAdmin,
    isSuperAdmin,
    restore,
    bumpAvatar,
    login,
    register,
    fetchProfile,
    updateProfile,
    uploadAvatar,
    logout
  }
})
