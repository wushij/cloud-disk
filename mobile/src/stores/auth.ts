import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { request, TOKEN_KEY, USER_KEY, NICKNAME_KEY, ROLE_KEY, uploadFile, fileApiUrl } from '@/api/http'
import { clearMediaTokenCache, ensureMediaToken, refreshMediaToken, mediaTokenRef } from '@/utils/mediaToken'
import {
  loadAvatarThumb,
  clearAvatarThumb,
  cacheAvatarFromPath,
  cacheAvatarFromUrl
} from '@/utils/avatarCache'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const username = ref<string | null>(null)
  const nickname = ref<string | null>(null)
  const role = ref<string | null>(null)
  const hasAvatar = ref(false)
  const avatarVersion = ref(0)
  const avatarCachedSrc = ref('')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN' || role.value === 'SUPER_ADMIN')
  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')
  const displayName = computed(() => nickname.value || username.value || '用户')

  const avatarSrc = computed(() => {
    if (!hasAvatar.value) return ''
    const t = mediaTokenRef.value
    if (!t) return ''
    return fileApiUrl('/api/auth/avatar/view') + `&v=${avatarVersion.value}`
  })

  const avatarDisplaySrc = computed(() => avatarSrc.value || avatarCachedSrc.value)

  const avatarReady = computed(() => hasAvatar.value && !!avatarSrc.value)

  function restoreAvatarCache() {
    avatarCachedSrc.value = ''
    if (!hasAvatar.value || !username.value) return
    avatarCachedSrc.value = loadAvatarThumb(username.value, avatarVersion.value)
  }

  function restore() {
    token.value = uni.getStorageSync(TOKEN_KEY) || null
    username.value = uni.getStorageSync(USER_KEY) || null
    nickname.value = uni.getStorageSync(NICKNAME_KEY) || null
    role.value = uni.getStorageSync(ROLE_KEY) || null
    avatarVersion.value = Number(uni.getStorageSync('cd_avatar_version') || '0')
    hasAvatar.value = uni.getStorageSync('cd_has_avatar') === 'true'
    restoreAvatarCache()
    if (token.value) {
      void ensureMediaToken()
    }
  }

  function persist() {
    if (token.value) uni.setStorageSync(TOKEN_KEY, token.value)
    else uni.removeStorageSync(TOKEN_KEY)
    if (username.value) uni.setStorageSync(USER_KEY, username.value)
    else uni.removeStorageSync(USER_KEY)
    if (nickname.value) uni.setStorageSync(NICKNAME_KEY, nickname.value)
    else uni.removeStorageSync(NICKNAME_KEY)
    if (role.value) uni.setStorageSync(ROLE_KEY, role.value)
    else uni.removeStorageSync(ROLE_KEY)
    uni.setStorageSync('cd_avatar_version', String(avatarVersion.value))
    uni.setStorageSync('cd_has_avatar', String(hasAvatar.value))
  }

  function applyProfile(data: { username?: string; nickname?: string; role?: string; avatar?: string | null }) {
    if (data.username) username.value = data.username
    nickname.value = data.nickname || data.username || nickname.value
    role.value = data.role || role.value || 'USER'
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
    hasAvatar.value = true
    persist()
  }

  watch(avatarSrc, (url) => {
    if (!url || !username.value) return
    void cacheAvatarFromUrl(username.value, avatarVersion.value, url)
      .then((data) => {
        avatarCachedSrc.value = data
      })
      .catch(() => {})
  })

  async function login(
    u: string,
    p: string,
    captcha?: { captchaId?: string; captchaAnswer?: string }
  ) {
    const data = await request<{ token: string; username: string; nickname?: string; role?: string }>({
      url: '/api/auth/login',
      method: 'POST',
      data: { username: u, password: p, ...captcha },
      skipAuth: true,
      skipErrorHandler: true
    })
    token.value = data.token
    username.value = data.username
    nickname.value = data.nickname || data.username
    role.value = data.role || 'USER'
    persist()
    await fetchProfile()
    await refreshMediaToken()
  }

  async function register(
    u: string,
    p: string,
    nick?: string,
    captcha?: { captchaId?: string; captchaAnswer?: string }
  ) {
    const data = await request<{ token?: string; username?: string; nickname?: string; role?: string; pending?: boolean; title?: string; message?: string }>({
      url: '/api/auth/register',
      method: 'POST',
      data: { username: u, password: p, nickname: nick, ...captcha },
      skipAuth: true,
      skipErrorHandler: true
    })
    if (data.pending) {
      return data
    }
    token.value = data.token!
    username.value = data.username!
    nickname.value = data.nickname || data.username!
    role.value = data.role || 'USER'
    persist()
    await fetchProfile()
    await refreshMediaToken()
    return data
  }

  async function fetchProfile() {
    const data = await request<{ username?: string; nickname?: string; role?: string; avatar?: string | null }>({
      url: '/api/auth/me'
    })
    applyProfile(data)
    return data
  }

  async function uploadAvatar(filePath: string) {
    const u = username.value
    const data = await uploadFile({
      url: '/api/auth/avatar',
      filePath,
      name: 'file'
    }) as { username?: string; nickname?: string; role?: string; avatar?: string | null }
    bumpAvatar()
    applyProfile(data)
    if (u) {
      try {
        avatarCachedSrc.value = await cacheAvatarFromPath(u, avatarVersion.value, filePath)
      } catch {
        /* ignore */
      }
    }
    await ensureMediaToken()
  }

  async function logout() {
    try {
      await request({
        url: '/api/auth/logout',
        method: 'POST'
      })
    } catch {
      /* ignore */
    }
    token.value = null
    username.value = null
    nickname.value = null
    role.value = null
    hasAvatar.value = false
    avatarVersion.value = 0
    avatarCachedSrc.value = ''
    uni.removeStorageSync(TOKEN_KEY)
    uni.removeStorageSync(USER_KEY)
    uni.removeStorageSync(NICKNAME_KEY)
    uni.removeStorageSync(ROLE_KEY)
    uni.removeStorageSync('cd_avatar_version')
    uni.removeStorageSync('cd_has_avatar')
    clearAvatarThumb()
    clearMediaTokenCache()
  }

  function requireLogin() {
    restore()
    if (!token.value) {
      uni.reLaunch({ url: '/pages/login/index' })
      return false
    }
    return true
  }

  return {
    token,
    username,
    nickname,
    role,
    hasAvatar,
    avatarVersion,
    avatarSrc,
    avatarDisplaySrc,
    avatarCachedSrc,
    avatarReady,
    isLoggedIn,
    isAdmin,
    isSuperAdmin,
    displayName,
    restore,
    restoreAvatarCache,
    login,
    register,
    fetchProfile,
    uploadAvatar,
    logout,
    requireLogin,
    ensureMediaToken,
    refreshMediaToken
  }
})

