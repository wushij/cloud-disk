<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Connection, Link, Message, Key } from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import { useAuthStore } from '@/stores/auth'
import { useConfirmDialogStore } from '@/stores/confirmDialog'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import AuthCaptchaField from '@/components/AuthCaptchaField.vue'
import EmailCodeBtn from '@/components/EmailCodeBtn.vue'
import ForgotPasswordModal from '@/components/ForgotPasswordModal.vue'
import { authApi } from '@/api'
import { getApiErrorMessage } from '@/utils/error'
import { validateRegisterUsername } from '@/utils/username'
import { toCaptchaDataUrl } from '@/utils/captcha'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const confirmDialog = useConfirmDialogStore()

const mode = ref<'login' | 'register'>('login')
const loginType = ref<'password' | 'email'>('password')

const username = ref('')
const password = ref('')
const nickname = ref('')

const email = ref('')
const emailCode = ref('')

const registerEmail = ref('')
const registerEmailCode = ref('')
const showForgotModal = ref(false)

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
  const data = await authApi.captcha()
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
  if (loginType.value === 'email') {
    showCaptcha.value = false
    return
  }
  try {
    const data = await authApi.captchaRequired()
    showCaptcha.value = !!data.required
    if (showCaptcha.value) await refreshCaptcha()
  } catch {
    showCaptcha.value = false
  }
}

watch([mode, loginType], () => {
  void syncCaptchaState()
})

function onForgotSuccess(payload: { email: string; newPassword: string }) {
  mode.value = 'login'
  loginType.value = 'password'
  username.value = payload.email
  password.value = payload.newPassword
}

async function loadProviders() {
  try {
    const data = await authApi.providers()
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
    const data = await authApi.ssoTicket(ticket)
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

  if (mode.value === 'register') {
    const u = username.value.trim()
    const p = password.value
    if (!u || !p) {
      ElMessage.warning('请填写用户名和密码')
      return
    }
    const usernameError = validateRegisterUsername(u)
    if (usernameError) {
      ElMessage.warning(usernameError)
      return
    }
    if (showCaptcha.value && !captchaAnswer.value.trim()) {
      ElMessage.warning('请完成图形验证码')
      return
    }
    if (registerEmail.value.trim() && !registerEmailCode.value.trim()) {
      ElMessage.warning('已填写邮箱，请填写入验证码')
      return
    }

    loading.value = true
    try {
      const captchaPayload = showCaptcha.value
        ? { captchaId: captchaId.value, captchaAnswer: captchaAnswer.value }
        : {}
      const data = await auth.register(
        u,
        p,
        nickname.value.trim() || undefined,
        captchaPayload,
        {
          email: registerEmail.value.trim() || undefined,
          emailCode: registerEmailCode.value.trim() || undefined
        }
      )
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
        registerEmail.value = ''
        registerEmailCode.value = ''
        await syncCaptchaState()
        return
      }
      ElMessage.success('注册成功')
      const redirect = route.query.redirect as string
      router.replace(redirect && redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/disk')
    } catch (e: unknown) {
      ElMessage.error(getApiErrorMessage(e))
      await syncCaptchaState()
    } finally {
      loading.value = false
    }
    return
  }

  // Login mode
  loading.value = true
  try {
    if (loginType.value === 'password') {
      const u = username.value.trim()
      const p = password.value
      if (!u || !p) {
        ElMessage.warning('请输入用户名/邮箱和密码')
        loading.value = false
        return
      }
      const captchaPayload = showCaptcha.value
        ? { captchaId: captchaId.value, captchaAnswer: captchaAnswer.value }
        : {}
      await auth.login(u, p, captchaPayload)
      ElMessage.success('登录成功')
    } else {
      const em = email.value.trim()
      const code = emailCode.value.trim()
      if (!em || !code) {
        ElMessage.warning('请输入邮箱和验证码')
        loading.value = false
        return
      }
      await auth.loginByEmailCode(em, code)
      ElMessage.success('邮箱快捷登录成功')
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
            <BrandMark :size="40" />
          </div>
          <h1>CloudDisk Pro</h1>
          <p class="auth-brand-desc">企业级智能云盘 · 安全存储 · 高效协作</p>
          <ul class="auth-features">
            <li>大文件分片上传与 MD5 秒传</li>
            <li>在线预览与 Office 协同编辑</li>
            <li>团队空间与外链分享</li>
          </ul>
        </div>
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

        <!-- 登录模式下的 单层高质感胶囊切片切换器 -->
        <div v-if="mode === 'login'" class="segmented-control">
          <button
            type="button"
            class="segmented-btn"
            :class="{ active: loginType === 'password' }"
            @click="loginType = 'password'"
          >
            <el-icon class="seg-icon"><User /></el-icon>
            账号密码登录
          </button>
          <button
            type="button"
            class="segmented-btn"
            :class="{ active: loginType === 'email' }"
            @click="loginType = 'email'"
          >
            <el-icon class="seg-icon"><Message /></el-icon>
            邮箱验证码登录
          </button>
        </div>

        <el-form class="auth-form" @submit.prevent="submit">
          <!-- 1. 账号密码登录 -->
          <template v-if="mode === 'login' && loginType === 'password'">
            <el-form-item>
              <el-input
                v-model="username"
                placeholder="用户名/电子邮箱"
                autocomplete="username"
                size="large"
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
                      width="15"
                      height="15"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.75"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                      <circle cx="12" cy="12" r="3" />
                    </svg>
                    <svg
                      v-else
                      viewBox="0 0 24 24"
                      width="15"
                      height="15"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.75"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                      <circle cx="12" cy="12" r="3" />
                      <line x1="3" y1="4" x2="21" y2="20" />
                    </svg>
                  </button>
                </template>
              </el-input>
            </el-form-item>

            <!-- 图形验证码 (防刷) -->
            <el-form-item v-show="showCaptcha" class="auth-captcha-item">
              <AuthCaptchaField
                v-model="captchaAnswer"
                :captcha-img="captchaImg"
                @refresh="refreshCaptcha"
                @enter="submit"
              />
            </el-form-item>

            <div class="forgot-pwd-row">
              <button type="button" class="forgot-btn" @click="mode = 'register'">
                没有账号？立即注册
              </button>
              <button type="button" class="forgot-btn" @click="showForgotModal = true">
                忘记密码？
              </button>
            </div>
          </template>

          <!-- 2. 邮箱验证码登录 -->
          <template v-if="mode === 'login' && loginType === 'email'">
            <el-form-item>
              <el-input
                v-model="email"
                placeholder="电子邮箱"
                size="large"
                autocomplete="email"
                :prefix-icon="Message"
              />
            </el-form-item>

            <el-form-item class="code-item">
              <div class="code-input-group">
                <el-input
                  v-model="emailCode"
                  placeholder="6 位邮箱验证码"
                  size="large"
                  maxlength="6"
                  autocomplete="one-time-code"
                  :prefix-icon="Key"
                />
                <EmailCodeBtn :email="email" scene="login" />
              </div>
            </el-form-item>

            <div class="forgot-pwd-row">
              <button type="button" class="forgot-btn" @click="mode = 'register'">
                没有账号？立即注册
              </button>
              <button type="button" class="forgot-btn" @click="showForgotModal = true">
                忘记密码？
              </button>
            </div>
          </template>

          <!-- 3. 账号注册 -->
          <template v-if="mode === 'register'">
            <el-form-item class="auth-nickname-item">
              <el-input v-model="nickname" placeholder="昵称（可选）" size="large" :prefix-icon="User" />
            </el-form-item>

            <el-form-item>
              <el-input
                v-model="username"
                placeholder="用户名 (4-12位)"
                autocomplete="username"
                size="large"
                maxlength="12"
                :prefix-icon="User"
              />
            </el-form-item>

            <el-form-item>
              <el-input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="密码 (6-64位)"
                autocomplete="new-password"
                size="large"
                :prefix-icon="Lock"
              />
            </el-form-item>

            <el-form-item>
              <el-input
                v-model="registerEmail"
                placeholder="电子邮箱 (推荐绑定)"
                size="large"
                autocomplete="email"
                :prefix-icon="Message"
              />
            </el-form-item>

            <el-form-item v-if="registerEmail.trim()" class="code-item">
              <div class="code-input-group">
                <el-input
                  v-model="registerEmailCode"
                  placeholder="邮箱验证码"
                  size="large"
                  maxlength="6"
                  autocomplete="one-time-code"
                  :prefix-icon="Key"
                />
                <EmailCodeBtn :email="registerEmail" scene="register" />
              </div>
            </el-form-item>

            <!-- 图形验证码 (防刷) -->
            <el-form-item v-show="showCaptcha" class="auth-captcha-item">
              <AuthCaptchaField
                v-model="captchaAnswer"
                :captcha-img="captchaImg"
                @refresh="refreshCaptcha"
                @enter="submit"
              />
            </el-form-item>

            <div class="forgot-pwd-row single-right">
              <button type="button" class="forgot-btn" @click="mode = 'login'">
                已有账号？去登录
              </button>
            </div>
          </template>

          <el-button
            type="primary"
            class="auth-submit"
            size="large"
            :loading="loading"
            native-type="submit"
          >
            {{ mode === 'login' ? (loginType === 'password' ? '登 录' : '快捷登录') : '注 册' }}
          </el-button>
        </el-form>

        <!-- 企业登录接入 -->
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

  <!-- 重置/找回密码 抽离弹窗组件 -->
  <ForgotPasswordModal v-model="showForgotModal" @success="onForgotSuccess" />

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
  min-height: 600px;
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

.auth-brand {
  position: relative;
  padding: 48px 40px;
  background: rgba(255, 251, 251, 0.88);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  color: #0f172a;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  overflow: hidden;
  border-right: 1px solid rgba(240, 212, 212, 0.72);
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
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
  border: 1px solid rgba(240, 212, 212, 0.72);
  box-shadow: 0 6px 28px rgba(239, 68, 68, 0.08);
  animation: gentleFloat 4s ease-in-out infinite;
}

@keyframes gentleFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.auth-brand h1 {
  margin: 0 0 14px;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.5px;
  line-height: 1.2;
  color: #0f172a;
}

.auth-brand-desc {
  margin: 0 0 40px;
  font-size: 15px;
  line-height: 1.6;
  color: #64748b;
}

.auth-features {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  max-width: 290px;
  counter-reset: feature;
}

.auth-features li {
  position: relative;
  padding: 12px 16px 12px 48px;
  font-size: 14px;
  color: #334155;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(240, 212, 212, 0.55);
  border-radius: 999px;
  line-height: 1.5;
  text-align: left;
  counter-increment: feature;
}

.auth-features li::before {
  content: counter(feature);
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ffffff;
  border: 1.5px solid rgba(240, 212, 212, 0.85);
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

/* ---- 右侧表单区 ---- */
.auth-panel {
  padding: 44px 40px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.auth-tabs {
  display: flex;
  justify-content: center;
  gap: 36px;
  margin-bottom: 24px;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 8px;
}

.auth-tab {
  background: none;
  border: none;
  font-size: 20px;
  font-weight: 700;
  color: #94a3b8;
  cursor: pointer;
  position: relative;
  padding-bottom: 10px;
  transition: all 0.25s ease;
  letter-spacing: -0.3px;
}

.auth-tab.active {
  color: #0f172a;
}

.auth-tab-line {
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--el-color-primary, var(--cd-primary, #0f172a));
  border-radius: 999px;
  display: none;
}

.auth-tab.active .auth-tab-line {
  display: block;
}

/* ---- 标准单层高质感胶囊切片 (灰底+选中白块浮动) ---- */
.segmented-control {
  display: flex;
  background: #f1f5f9;
  padding: 3px;
  border-radius: 999px;
  margin-bottom: 24px;
  border: none;
}

.segmented-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  background: transparent;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.segmented-btn:hover {
  color: #1e293b;
}

.segmented-btn.active {
  background: #ffffff;
  color: var(--el-color-primary, #2563eb);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  border: none;
}

.seg-icon {
  font-size: 14px;
}

.auth-form {
  display: flex;
  flex-direction: column;
}

/* 彻底还原为原版的 【长圆椭圆边框】 (border-radius: 999px) */
:deep(.el-input__wrapper) {
  border-radius: 999px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 4px 20px !important;
  transition: all 0.2s ease !important;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 inset !important;
  background-color: #ffffff !important;
}

:deep(.el-input__wrapper.is-focus) {
  background-color: #ffffff !important;
  box-shadow: 0 0 0 2px #0f172a inset, 0 0 12px rgba(15, 23, 42, 0.1) !important;
}

:deep(.el-input__inner) {
  font-size: 14px !important;
  color: #0f172a !important;
}

/* 消除浏览器 Autofill 自动填充蓝色背景 */
:deep(input:-webkit-autofill),
:deep(input:-webkit-autofill:hover),
:deep(input:-webkit-autofill:focus) {
  -webkit-box-shadow: 0 0 0px 1000px #f8fafc inset !important;
  -webkit-text-fill-color: #0f172a !important;
}

.forgot-pwd-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: -6px;
  margin-bottom: 8px;
}

.forgot-pwd-row.single-right {
  justify-content: flex-end;
}

.forgot-btn {
  background: none;
  border: none;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  padding: 0;
  transition: color 0.2s;
}

.forgot-btn:hover {
  color: #0f172a;
  text-decoration: underline;
}

.code-input-group {
  display: flex;
  gap: 12px;
  width: 100%;
}

.auth-eye-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #94a3b8;
  padding: 0;
  display: flex;
  align-items: center;
  transition: color 0.2s;
}

.auth-eye-btn:hover {
  color: #475569;
}

/* ---- 长圆主按钮 (border-radius: 999px) ---- */
.auth-submit {
  width: 100%;
  margin-top: 8px;
  height: 48px !important;
  font-size: 16px !important;
  font-weight: 600 !important;
  border-radius: 999px !important;
  box-shadow: 0 8px 20px -4px rgba(0, 0, 0, 0.18) !important;
  transition: all 0.25s ease !important;
}

.auth-submit:hover {
  box-shadow: 0 12px 24px -4px rgba(0, 0, 0, 0.25) !important;
  transform: translateY(-1px);
}

.auth-submit:active {
  transform: translateY(0);
}

.auth-fed {
  margin-top: 28px;
}

.auth-divider {
  position: relative;
  text-align: center;
  margin-bottom: 18px;
}

.auth-divider::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  right: 0;
  height: 1px;
  background: #f1f5f9;
}

.auth-divider span {
  position: relative;
  background: #ffffff;
  padding: 0 14px;
  font-size: 12px;
  color: #94a3b8;
}

.auth-fed-btns {
  display: flex;
  gap: 12px;
}

.auth-fed-btns .el-button {
  flex: 1;
  border-radius: 999px !important;
}

@keyframes floatUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
