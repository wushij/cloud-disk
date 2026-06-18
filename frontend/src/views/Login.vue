<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Cloudy, Connection, Link, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import http, { TOKEN_KEY, USER_KEY, NICKNAME_KEY, ROLE_KEY } from '@/api/http'
import { getApiErrorMessage } from '@/utils/error'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const loading = ref(false)
const ldapEnabled = ref(false)
const ssoEnabled = ref(false)
const ssoAuthorizeUrl = ref('')
const ssoProviderName = ref('SSO')
const captchaId = ref('')
const captchaQuestion = ref('')
const captchaAnswer = ref('')
const showCaptcha = ref(false)

async function refreshCaptcha() {
  const { data } = await http.get('/api/auth/captcha', { skipErrorHandler: true })
  captchaId.value = data.id
  captchaQuestion.value = data.question
  captchaAnswer.value = ''
}

async function syncCaptchaState() {
  if (mode.value === 'register') {
    showCaptcha.value = true
    await refreshCaptcha()
    return
  }
  try {
    const { data } = await http.get('/api/auth/captcha/required', { skipErrorHandler: true })
    showCaptcha.value = !!data.required
    if (showCaptcha.value) await refreshCaptcha()
  } catch {
    showCaptcha.value = false
  }
}

watch(mode, () => {
  void syncCaptchaState()
})

async function loadProviders() {
  try {
    const { data } = await http.get('/api/auth/providers', { skipErrorHandler: true })
    ldapEnabled.value = !!data.ldapEnabled
    ssoEnabled.value = !!data.ssoEnabled
    if (data.sso?.authorizeUrl) {
      ssoAuthorizeUrl.value = data.sso.authorizeUrl
      ssoProviderName.value = data.sso.providerName || 'SSO'
    }
  } catch {
    /* optional */
  }
}

function applySsoTokenFromQuery() {
  const token = route.query.token as string
  if (!token) return
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, (route.query.username as string) || '')
  localStorage.setItem(NICKNAME_KEY, (route.query.nickname as string) || (route.query.username as string) || '')
  auth.restore()
  router.replace('/disk')
}

async function submit() {
  if (loading.value) return

  const u = username.value.trim()
  const p = password.value
  const isRegister = mode.value === 'register'

  if (!u || !p) {
    ElMessage.warning(isRegister ? '请填写用户名和密码' : '请输入账号和密码')
    return
  }
  if (showCaptcha.value && !captchaAnswer.value.trim()) {
    ElMessage.warning('请完成验证码')
    return
  }

  loading.value = true
  try {
    const captchaPayload = showCaptcha.value
      ? { captchaId: captchaId.value, captchaAnswer: captchaAnswer.value }
      : {}

    if (mode.value === 'login') {
      await auth.login(u, p, captchaPayload)
      ElMessage.success('登录成功')
    } else {
      await auth.register(u, p, nickname.value.trim() || undefined, captchaPayload)
      ElMessage.success('注册成功')
    }

    const redirect = route.query.redirect as string
    router.replace(redirect && redirect.startsWith('/') ? redirect : '/disk')
  } catch (e: unknown) {
    ElMessage.error(getApiErrorMessage(e))
    await syncCaptchaState()
  } finally {
    loading.value = false
  }
}

async function ldapLogin() {
  const u = username.value.trim()
  const p = password.value
  if (!u || !p) {
    ElMessage.warning('请输入 LDAP 账号和密码')
    return
  }
  loading.value = true
  try {
    const { data } = await http.post(
      '/api/auth/ldap/login',
      {
        username: u,
        password: p,
        captchaId: showCaptcha.value ? captchaId.value : undefined,
        captchaAnswer: showCaptcha.value ? captchaAnswer.value : undefined
      },
      { skipErrorHandler: true }
    )
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(USER_KEY, data.username)
    localStorage.setItem(NICKNAME_KEY, data.nickname || data.username)
    localStorage.setItem(ROLE_KEY, data.role || 'USER')
    auth.restore()
    ElMessage.success('LDAP 登录成功')
    router.replace('/disk')
  } catch (e: unknown) {
    ElMessage.error(getApiErrorMessage(e, 'LDAP 登录失败'))
    await syncCaptchaState()
  } finally {
    loading.value = false
  }
}

function startSso() {
  if (ssoAuthorizeUrl.value) {
    window.location.href = ssoAuthorizeUrl.value
  }
}

onMounted(() => {
  applySsoTokenFromQuery()
  loadProviders()
  void syncCaptchaState()
})
</script>

<template>
  <div class="auth-page">
    <div class="auth-bg">
      <div class="auth-bg-grid" />
      <div class="auth-glow auth-glow-a" />
      <div class="auth-glow auth-glow-b" />
    </div>

    <div class="auth-shell">
      <!-- 左侧品牌区 -->
      <section class="auth-brand">
        <div class="auth-brand-inner">
          <div class="auth-logo">
            <el-icon :size="30"><Cloudy /></el-icon>
          </div>
          <h1>CloudDisk Pro</h1>
          <p class="auth-brand-desc">企业级智能云盘 · 安全存储 · 高效协作</p>
          <ul class="auth-features">
            <li>大文件分片上传与 MD5 秒传</li>
            <li>在线预览与 Office 协同编辑</li>
            <li>团队空间与外链分享</li>
          </ul>
        </div>
        <div class="auth-brand-deco" />
      </section>

      <!-- 右侧表单区 -->
      <section class="auth-panel">
        <nav class="auth-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            class="auth-tab"
            :class="{ active: mode === 'login' }"
            @click="mode = 'login'"
          >登录</button>
          <button
            type="button"
            role="tab"
            class="auth-tab"
            :class="{ active: mode === 'register' }"
            @click="mode = 'register'"
          >注册</button>
          <span class="auth-tab-slider" :class="mode" />
        </nav>

        <div class="auth-panel-head">
          <h2>{{ mode === 'login' ? '欢迎回来' : '创建新账户' }}</h2>
          <p>{{ mode === 'login' ? '使用您的账号登录云盘' : '填写信息完成注册' }}</p>
        </div>

        <el-form class="auth-form" @submit.prevent="submit">
          <div class="auth-nickname-slot" :class="{ 'is-visible': mode === 'register' }">
            <el-form-item class="auth-nickname-item">
              <el-input v-model="nickname" placeholder="昵称（可选）" size="large" :prefix-icon="User" />
            </el-form-item>
          </div>

          <el-form-item>
            <el-input
              v-model="username"
              placeholder="用户名"
              autocomplete="username"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>

          <el-form-item>
            <el-input
              v-model="password"
              type="password"
              placeholder="密码"
              show-password
              autocomplete="current-password"
              size="large"
              :prefix-icon="Lock"
            />
          </el-form-item>

          <div class="auth-captcha-slot" :class="{ 'is-visible': showCaptcha }">
            <el-form-item v-show="showCaptcha" class="auth-captcha-item">
              <div class="auth-captcha">
                <div class="auth-captcha-q">
                  <span>{{ captchaQuestion }}</span>
                  <button type="button" class="auth-captcha-refresh" title="换一题" @click="refreshCaptcha">
                    <el-icon><Refresh /></el-icon>
                  </button>
                </div>
                <el-input v-model="captchaAnswer" placeholder="计算结果" size="large" />
              </div>
            </el-form-item>
          </div>

          <el-button
            type="primary"
            class="auth-submit"
            size="large"
            :loading="loading"
            native-type="submit"
          >
            {{ mode === 'login' ? '登 录' : '注 册' }}
          </el-button>
        </el-form>

        <div v-if="mode === 'login' && (ldapEnabled || ssoEnabled)" class="auth-fed">
          <div class="auth-divider"><span>企业登录</span></div>
          <div class="auth-fed-btns">
            <el-button v-if="ldapEnabled" size="large" :loading="loading" @click="ldapLogin">
              <el-icon><Connection /></el-icon>
              LDAP
            </el-button>
            <el-button v-if="ssoEnabled" type="success" size="large" @click="startSso">
              <el-icon><Link /></el-icon>
              {{ ssoProviderName }}
            </el-button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px 24px;
  position: relative;
  overflow: hidden;
  background: #0f172a;
}

.auth-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.auth-bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at center, black 20%, transparent 75%);
}

.auth-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
}

.auth-glow-a {
  width: 420px;
  height: 420px;
  background: #4f7cff;
  top: -100px;
  right: 8%;
}

.auth-glow-b {
  width: 360px;
  height: 360px;
  background: #6366f1;
  bottom: -80px;
  left: 5%;
}

.auth-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(340px, 1fr) minmax(400px, 440px);
  width: min(920px, 100%);
  height: 580px;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.4);
  animation: floatUp 0.55s ease;
}

/* ---- 左侧品牌 ---- */
.auth-brand {
  position: relative;
  padding: 48px 40px;
  background: linear-gradient(145deg, #1e3a8a 0%, #312e81 50%, #1e1b4b 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
}

.auth-brand-deco {
  position: absolute;
  right: -60px;
  bottom: -60px;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
  pointer-events: none;
}

.auth-brand-inner {
  position: relative;
  z-index: 1;
}

.auth-logo {
  width: 58px;
  height: 58px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.auth-brand h1 {
  margin: 0 0 12px;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0.3px;
  line-height: 1.2;
}

.auth-brand-desc {
  margin: 0 0 36px;
  font-size: 15px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.72);
}

.auth-features {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.auth-features li {
  position: relative;
  padding-left: 24px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.5;
}

.auth-features li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 9px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #93c5fd;
  box-shadow: 0 0 10px rgba(147, 197, 253, 0.7);
}

/* ---- 右侧表单 ---- */
.auth-panel {
  background: #fff;
  padding: 44px 40px 36px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
}

.auth-form {
  flex-shrink: 0;
  min-height: 348px;
}

.auth-tabs {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #f1f5f9;
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 28px;
}

.auth-tab {
  position: relative;
  z-index: 1;
  border: none;
  background: transparent;
  padding: 11px 0;
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
  cursor: pointer;
  transition: color 0.25s ease;
}

.auth-tab.active {
  color: #4f7cff;
}

.auth-tab-slider {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.1);
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  pointer-events: none;
}

.auth-tab-slider.register {
  transform: translateX(100%);
}

.auth-panel-head {
  margin-bottom: 24px;
}

.auth-panel-head h2 {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 700;
  color: #1a1d26;
}

.auth-panel-head p {
  margin: 0;
  font-size: 14px;
  color: #8b92a5;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-nickname-slot,
.auth-captcha-slot {
  height: 0;
  overflow: hidden;
}

.auth-nickname-slot.is-visible,
.auth-captcha-slot.is-visible {
  height: 66px;
}

.auth-nickname-item,
.auth-captcha-item {
  margin-bottom: 0 !important;
}

.auth-form :deep(.el-input__wrapper) {
  height: 48px;
  border-radius: 12px !important;
  background: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  transition: background-color 0.15s ease !important;
}

.auth-form :deep(.el-input__wrapper:hover) {
  background: #f1f5f9 !important;
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  background: #fff !important;
  box-shadow: 0 0 0 2px #4f7cff inset !important;
}

.auth-form :deep(.el-input__inner) {
  color: #1a1d26;
}

.auth-form :deep(.el-input__prefix .el-icon) {
  color: #94a3b8;
}

.auth-captcha {
  display: flex;
  gap: 10px;
  width: 100%;
}

.auth-captcha-q {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 108px;
  height: 48px;
  padding: 0 10px;
  border-radius: 12px;
  background: linear-gradient(135deg, #eff6ff, #f0f9ff);
  border: 1px solid #bfdbfe;
  font-weight: 700;
  font-size: 15px;
  color: #1d4ed8;
  flex-shrink: 0;
}

.auth-captcha-refresh {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: rgba(79, 124, 255, 0.12);
  color: #4f7cff;
  cursor: pointer;
  transition: background 0.2s;
}

.auth-captcha-refresh:hover {
  background: rgba(79, 124, 255, 0.22);
}

.auth-captcha .el-input {
  flex: 1;
}

.auth-submit {
  width: 100%;
  height: 48px !important;
  margin-top: 6px;
  font-size: 15px !important;
  font-weight: 600 !important;
  letter-spacing: 3px;
  border-radius: 12px !important;
}

.auth-panel :deep(.auth-submit.el-button--primary:hover),
.auth-panel :deep(.auth-submit.el-button--primary:focus) {
  transform: none !important;
}

.auth-fed {
  margin-top: 24px;
}

.auth-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  font-size: 12px;
  color: #8b92a5;
}

.auth-divider::before,
.auth-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e8ecf2;
}

.auth-fed-btns {
  display: flex;
  gap: 10px;
}

.auth-fed-btns .el-button {
  flex: 1;
  height: 44px !important;
  border-radius: 12px !important;
}

@media (max-width: 820px) {
  .auth-shell {
    grid-template-columns: 1fr;
    max-width: 440px;
    height: auto;
    min-height: 520px;
  }

  .auth-brand {
    display: none;
  }

  .auth-panel {
    padding: 36px 28px 28px;
  }
}
</style>
