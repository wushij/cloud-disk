<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Cloudy, Connection, Link } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useConfirmDialogStore } from '@/stores/confirmDialog'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import AuthCaptchaField from '@/components/AuthCaptchaField.vue'
import http from '@/api/http'
import { getApiErrorMessage } from '@/utils/error'
import { validateRegisterUsername } from '@/utils/username'
import { toCaptchaDataUrl } from '@/utils/captcha'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const confirmDialog = useConfirmDialogStore()

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
const captchaImg = ref('')
const captchaAnswer = ref('')
const showCaptcha = ref(false)
const showPassword = ref(false)

async function refreshCaptcha() {
  const { data } = await http.get(`/api/auth/captcha?_=${Date.now()}`, { skipErrorHandler: true })
  captchaId.value = data.id
  captchaImg.value = toCaptchaDataUrl(data.img)
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

async function applySsoTokenFromQuery() {
  const ticket = route.query.sso_ticket as string
  if (!ticket) return
  loading.value = true
  try {
    const { data } = await http.post('/api/auth/sso/ticket', { ticket }, { skipErrorHandler: true })
    await auth.completeSsoSession(data)
    ElMessage.success('单点登录成功')
    router.replace('/disk')
  } catch (e: unknown) {
    ElMessage.error(getApiErrorMessage(e, '单点登录授权失效，请重试'))
  } finally {
    loading.value = false
  }
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
  if (isRegister) {
    const usernameError = validateRegisterUsername(u)
    if (usernameError) {
      ElMessage.warning(usernameError)
      return
    }
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
      const data = await auth.register(u, p, nickname.value.trim() || undefined, captchaPayload)
      if (data?.pending) {
        await confirmDialog.openAlert({
          title: data.title || '注册申请已提交',
          message: data.message || '管理员审核通过后您才能登录云盘，请耐心等待，无需重复注册。',
          confirmText: '我知道了',
          tone: 'info'
        })
        mode.value = 'login'
        password.value = ''
        nickname.value = ''
        await syncCaptchaState()
        return
      }
      ElMessage.success('注册成功')
    }

    const redirect = route.query.redirect as string
    router.replace(redirect && redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/disk')
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
    const captchaPayload = showCaptcha.value
      ? { captchaId: captchaId.value, captchaAnswer: captchaAnswer.value }
      : {}
    await auth.ldapLogin(u, p, captchaPayload)
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
      <div class="auth-glow auth-glow-c" />
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
        <div class="auth-brand-deco-1" />
        <div class="auth-brand-deco-2" />
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
          >
            登录
            <span class="auth-tab-line" />
          </button>
          <button
            type="button"
            role="tab"
            class="auth-tab"
            :class="{ active: mode === 'register' }"
            @click="mode = 'register'"
          >
            注册
            <span class="auth-tab-line" />
          </button>
        </nav>

        <el-form class="auth-form" @submit.prevent="submit">
          <div v-if="mode === 'login'" class="auth-welcome">
            <h2>Welcome Back</h2>
            <p>智能云端，即刻开启高效协作</p>
          </div>

          <el-form-item v-if="mode === 'register'" class="auth-nickname-item">
            <el-input v-model="nickname" placeholder="昵称（可选）" size="large" :prefix-icon="User" />
          </el-form-item>

          <el-form-item>
            <el-input
              v-model="username"
              placeholder="用户名"
              autocomplete="username"
              size="large"
              :maxlength="mode === 'register' ? 12 : undefined"
              :prefix-icon="User"
            />
          </el-form-item>

          <el-form-item>
            <el-input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="密码"
              autocomplete="current-password"
              size="large"
              :prefix-icon="Lock"
            >
              <template #suffix>
                <button
                  type="button"
                  class="auth-eye-btn"
                  tabindex="-1"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <svg
                    v-if="showPassword"
                    viewBox="0 0 24 24"
                    width="18"
                    height="18"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.75"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                  <svg
                    v-else
                    viewBox="0 0 24 24"
                    width="18"
                    height="18"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.75"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                    <circle cx="12" cy="12" r="3" />
                    <line x1="4" y1="5" x2="20" y2="19" />
                  </svg>
                </button>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item v-show="showCaptcha" class="auth-captcha-item">
            <AuthCaptchaField
              v-model="captchaAnswer"
              :captcha-img="captchaImg"
              @refresh="refreshCaptcha"
              @enter="submit"
            />
          </el-form-item>

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
  <ConfirmDialog />
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
  background: radial-gradient(ellipse 120% 100% at 50% 45%, #eef2f8 0%, #dde5f0 40%, #c8d6e8 70%, #b8c9df 100%);
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
    linear-gradient(rgba(100, 130, 180, 0.09) 1px, transparent 1px),
    linear-gradient(90deg, rgba(100, 130, 180, 0.09) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: radial-gradient(ellipse at center, black 40%, transparent 90%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 40%, transparent 90%);
}

.auth-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.35;
}

.auth-glow-a {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(147, 197, 253, 0.3) 0%, transparent 70%);
  top: -150px;
  right: 5%;
  animation: floatGlowA 20s ease-in-out infinite alternate;
}

.auth-glow-b {
  width: 440px;
  height: 440px;
  background: radial-gradient(circle, rgba(196, 181, 253, 0.25) 0%, transparent 70%);
  bottom: -120px;
  left: 2%;
  animation: floatGlowB 18s ease-in-out infinite alternate-reverse;
}

.auth-glow-c {
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(244, 143, 177, 0.15) 0%, transparent 70%);
  top: 40%;
  left: 40%;
  transform: translate(-50%, -50%);
  animation: floatGlowC 16s ease-in-out infinite alternate;
}

@keyframes floatGlowA {
  0% { transform: translateY(0) scale(1) rotate(0deg); }
  50% { transform: translateY(40px) scale(1.15) rotate(30deg); }
  100% { transform: translateY(-20px) scale(0.9) rotate(-15deg); }
}

@keyframes floatGlowB {
  0% { transform: translateY(0) scale(1.1) rotate(0deg); }
  50% { transform: translateY(-30px) scale(0.9) rotate(-45deg); }
  100% { transform: translateY(30px) scale(1.05) rotate(15deg); }
}

@keyframes floatGlowC {
  0% { transform: translate(-50%, -50%) scale(0.85) translate(-20px, -20px); }
  50% { transform: translate(-50%, -50%) scale(1.1) translate(35px, 30px); }
  100% { transform: translate(-50%, -50%) scale(0.9) translate(-30px, 15px); }
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
  box-shadow: 
    0 30px 70px rgba(100, 120, 150, 0.15), 
    0 10px 30px rgba(100, 120, 150, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  animation: floatUp 0.55s ease;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.45);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

/* ---- 左侧品牌 ---- */
.auth-brand {
  position: relative;
  padding: 48px 40px;
  background: linear-gradient(135deg, rgba(9, 13, 26, 0.88) 0%, rgba(17, 24, 39, 0.9) 50%, rgba(30, 27, 75, 0.92) 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  overflow: hidden;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

.auth-brand-deco-1 {
  position: absolute;
  top: -50px;
  left: -50px;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.25) 0%, transparent 70%);
  filter: blur(40px);
  pointer-events: none;
  animation: orbPulseA 12s ease-in-out infinite alternate;
}

.auth-brand-deco-2 {
  position: absolute;
  right: -80px;
  bottom: -80px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.2) 0%, transparent 70%);
  filter: blur(50px);
  pointer-events: none;
  animation: orbPulseB 12s ease-in-out infinite alternate;
}

@keyframes orbPulseA {
  0% { transform: scale(1) translate(0, 0); opacity: 0.7; }
  100% { transform: scale(1.15) translate(20px, 20px); opacity: 0.9; }
}

@keyframes orbPulseB {
  0% { transform: scale(1.15) translate(0, 0); opacity: 0.8; }
  100% { transform: scale(0.9) translate(-30px, -20px); opacity: 0.6; }
}

.auth-brand-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.auth-logo {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2), inset 0 1px 1px rgba(255, 255, 255, 0.2);
  color: #93c5fd;
  animation: gentleFloat 4s ease-in-out infinite;
}

.auth-brand h1 {
  margin: 0 0 14px;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.5px;
  line-height: 1.2;
  background: linear-gradient(135deg, #ffffff 30%, #a5b4fc 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.15);
}

.auth-brand-desc {
  margin: 0 0 40px;
  font-size: 15px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.6);
  letter-spacing: 0.5px;
}

.auth-features {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  max-width: 290px;
  align-items: stretch;
}

.auth-features li {
  position: relative;
  padding: 12px 16px 12px 42px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  line-height: 1.5;
  text-align: left;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: 
    0 4px 12px rgba(0, 0, 0, 0.1),
    inset 0 1px 1px rgba(255, 255, 255, 0.05);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.auth-features li:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.18);
  transform: translateY(-2px);
  box-shadow: 
    0 12px 28px rgba(0, 0, 0, 0.25),
    0 0 0 1px rgba(255, 255, 255, 0.1) inset;
  color: #ffffff;
}

.auth-features li::before {
  content: '✓';
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(79, 124, 255, 0.2), rgba(168, 85, 247, 0.2));
  color: #3b82f6;
  font-size: 12px;
  font-weight: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 8px rgba(59, 130, 246, 0.3);
  transition: transform 0.3s ease;
}

.auth-features li:hover::before {
  transform: translateY(-50%) scale(1.1) rotate(360deg);
  color: #818cf8;
}

/* ---- 右侧表单 ---- */
.auth-panel {
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  padding: 64px 40px 36px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  height: 100%;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

.auth-form {
  flex-shrink: 0;
  min-height: auto;
}

.auth-tabs {
  display: flex;
  justify-content: center;
  gap: 36px;
  margin-bottom: 24px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
  padding-bottom: 8px;
  flex-shrink: 0;
}

.auth-tab {
  position: relative;
  border: none;
  background: transparent;
  padding: 8px 16px;
  font-size: 16px;
  font-weight: 600;
  color: #64748b;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.auth-tab:hover {
  color: #0f172a;
}

.auth-tab.active {
  color: #2563eb;
  font-weight: 700;
}

.auth-tab-line {
  position: absolute;
  bottom: -9px;
  left: 12%;
  width: 76%;
  height: 3px;
  border-radius: 99px;
  background: linear-gradient(90deg, #2563eb, #4f46e5);
  transform: scaleX(0);
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.auth-tab.active .auth-tab-line {
  transform: scaleX(1);
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-nickname-item,
.auth-captcha-item {
  margin-bottom: 18px !important;
}

.auth-captcha-item :deep(.el-form-item__content) {
  line-height: normal;
}

.auth-form :deep(.el-input__wrapper) {
  height: 50px;
  border-radius: var(--cd-radius-full) !important;
  background: rgba(255, 255, 255, 0.45) !important;
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.15) inset !important;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1) !important;
  backdrop-filter: blur(4px);
}

.auth-form :deep(.el-input__wrapper:hover) {
  background: rgba(255, 255, 255, 0.7) !important;
  box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.25) inset !important;
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  background: #ffffff !important;
  transform: translateY(-1px);
  box-shadow:
    0 0 0 1px #3b82f6 inset,
    0 4px 16px rgba(59, 130, 246, 0.12) !important;
}

.auth-form :deep(.el-input__inner) {
  color: #1a1d26;
  font-weight: 500;
}

.auth-form :deep(.el-input__prefix .el-icon) {
  color: #94a3b8;
  font-size: 15px;
}

.auth-form :deep(.el-input__suffix) {
  color: #94a3b8;
}

.auth-eye-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-right: -2px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  padding: 0;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.auth-eye-btn:hover {
  color: #4b5563;
  background: rgba(148, 163, 184, 0.12);
}

.auth-submit {
  width: 100%;
  height: 50px !important;
  margin-top: 6px;
  font-size: 15px !important;
  font-weight: 700 !important;
  letter-spacing: 4px;
  border-radius: var(--cd-radius-full) !important;
  border: none !important;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 50%, #8b5cf6 100%) !important;
  background-size: 200% auto !important;
  color: #ffffff !important;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.25) !important;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

.auth-panel :deep(.auth-submit.el-button--primary:hover),
.auth-panel :deep(.auth-submit.el-button--primary:focus) {
  transform: translateY(-2px) !important;
  background-position: right center !important;
  box-shadow: 
    0 8px 24px rgba(99, 102, 241, 0.45),
    0 0 0 1px rgba(255, 255, 255, 0.1) inset !important;
}

.auth-panel :deep(.auth-submit.el-button--primary:active) {
  transform: translateY(0) !important;
}

.auth-welcome {
  text-align: center;
  margin-bottom: 24px;
  animation: fadeIn 0.6s ease;
  flex-shrink: 0;
}

.auth-welcome h2 {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.5px;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #2563eb 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.auth-welcome p {
  margin: 6px 0 0;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  letter-spacing: 0.5px;
}

.auth-fed {
  margin-top: 24px;
}

.auth-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1px;
  text-transform: uppercase;
  color: #8f9cae;
}

.auth-divider::before,
.auth-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: rgba(148, 163, 184, 0.15);
}

.auth-fed-btns {
  display: flex;
  gap: 12px;
}

.auth-fed-btns .el-button {
  flex: 1;
  height: 46px !important;
  border-radius: var(--cd-radius-full) !important;
  background: rgba(255, 255, 255, 0.4) !important;
  border: 1px solid rgba(148, 163, 184, 0.15) !important;
  transition: all 0.25s ease !important;
}

.auth-fed-btns .el-button:hover {
  background: rgba(255, 255, 255, 0.8) !important;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05) !important;
  border-color: rgba(59, 130, 246, 0.3) !important;
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
    background: rgba(255, 255, 255, 0.88);
  }
}
</style>
