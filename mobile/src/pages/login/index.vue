<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { request } from '@/api/http'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'
import { validateRegisterUsername } from '@/utils/username'
import { toCaptchaDataUrl } from '@/utils/captcha'

const auth = useAuthStore()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const loading = ref(false)
const showPass = ref(false)
const focusField = ref('')

const captchaId = ref('')
const captchaImg = ref('')
const captchaAnswer = ref('')
const showCaptcha = ref(false)

const pendingDialogVisible = ref(false)
const pendingDialogTitle = ref('注册申请已提交')
const pendingDialogMessage = ref('管理员审核通过后您才能登录云盘，请耐心等待，无需重复注册。')

async function refreshCaptcha() {
  const data = await request<{ id: string; img: string }>({
    url: `/api/auth/captcha?_=${Date.now()}`,
    skipAuth: true,
    skipErrorHandler: true
  })
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
    const data = await request<{ required?: boolean }>({
      url: '/api/auth/captcha/required',
      skipAuth: true,
      skipErrorHandler: true
    })
    showCaptcha.value = !!data.required
    if (showCaptcha.value) await refreshCaptcha()
  } catch {
    showCaptcha.value = false
  }
}

watch(mode, () => {
  void syncCaptchaState()
})

onMounted(() => {
  void syncCaptchaState()
})

async function submit() {
  if (loading.value) return

  const u = username.value.trim()
  const p = password.value
  const nick = nickname.value.trim()
  const isRegister = mode.value === 'register'

  if (!u || !p) {
    uni.showToast({ title: isRegister ? '请填写用户名和密码' : '请输入用户名和密码', icon: 'none' })
    return
  }
  if (isRegister) {
    const usernameError = validateRegisterUsername(u)
    if (usernameError) {
      uni.showToast({ title: usernameError, icon: 'none' })
      return
    }
  }
  if (showCaptcha.value && !captchaAnswer.value.trim()) {
    uni.showToast({ title: '请完成验证码', icon: 'none' })
    return
  }

  loading.value = true
  const captchaPayload = showCaptcha.value
    ? { captchaId: captchaId.value, captchaAnswer: captchaAnswer.value.trim() }
    : undefined

  try {
    if (mode.value === 'login') {
      await auth.login(u, p, captchaPayload)
      uni.showToast({ title: '登录成功', icon: 'success' })
      uni.reLaunch({ url: '/pages/disk/index' })
    } else {
      const res = await auth.register(u, p, nick || undefined, captchaPayload) as { pending?: boolean; title?: string; message?: string } | undefined
      if (res?.pending) {
        pendingDialogTitle.value = res.title || '注册申请已提交'
        pendingDialogMessage.value = res.message || '管理员审核通过后您才能登录云盘，请耐心等待，无需重复注册。'
        pendingDialogVisible.value = true
      } else {
        uni.showToast({ title: '注册成功', icon: 'success' })
        uni.reLaunch({ url: '/pages/disk/index' })
      }
    }
  } catch (e: any) {
    const msg = e instanceof Error ? e.message : (isRegister ? '注册失败' : '登录失败')
    uni.showToast({ title: msg, icon: 'none' })
    await syncCaptchaState()
  } finally {
    loading.value = false
  }
}

function onPendingDialogConfirm() {
  mode.value = 'login'
  password.value = ''
  nickname.value = ''
  void syncCaptchaState()
}
</script>

<template>
  <view class="page">
    <view class="bg-base">
      <view class="bg-grid" />
    </view>

    <view class="center-wrap">
      <view class="logo-float">
        <view class="logo-box">
          <svg width="34" height="34" viewBox="0 0 1024 1024">
            <path fill="#fff" d="M544 864V672h128L512 480 352 672h128v192H320v-1.6c-5.376.32-10.496 1.6-16 1.6A240 240 0 0 1 64 624c0-123.136 93.12-223.488 212.608-237.248A239.81 239.81 0 0 1 512 192a239.87 239.87 0 0 1 235.456 194.752c119.488 13.76 212.48 114.112 212.48 237.248a240 240 0 0 1-240 240c-5.376 0-10.56-1.28-16-1.6v1.6z" />
          </svg>
        </view>
      </view>

      <view class="card">
        <text class="card-title">CloudDisk Pro</text>

        <!-- 注册登录模式切换 -->
        <view class="auth-tabs">
          <view
            class="auth-tab"
            :class="{ active: mode === 'login' }"
            @click="mode = 'login'"
          >
            <text class="auth-tab-text">登录</text>
            <view class="auth-tab-line" />
          </view>
          <view
            class="auth-tab"
            :class="{ active: mode === 'register' }"
            @click="mode = 'register'"
          >
            <text class="auth-tab-text">注册</text>
            <view class="auth-tab-line" />
          </view>
        </view>

        <!-- 注册专用昵称输入 -->
        <view v-if="mode === 'register'" class="field" :class="{ focused: focusField === 'nickname' }">
          <view class="field-prefix">
            <u-icon name="account" size="19" color="#a0aec0" />
          </view>
          <input
            v-model="nickname"
            class="field-input"
            placeholder="昵称（可选）"
            placeholder-class="ph"
            @focus="focusField = 'nickname'"
            @blur="focusField = ''"
          />
        </view>

        <view class="field" :class="{ focused: focusField === 'user' }">
          <view class="field-prefix">
            <u-icon name="account" size="19" color="#a0aec0" />
          </view>
          <input
            v-model="username"
            class="field-input"
            placeholder="用户名"
            placeholder-class="ph"
            :maxlength="mode === 'register' ? 12 : 32"
            @focus="focusField = 'user'"
            @blur="focusField = ''"
          />
        </view>

        <view class="field" :class="{ focused: focusField === 'pass' }">
          <view class="field-prefix">
            <u-icon name="lock" size="19" color="#a0aec0" />
          </view>
          <input
            v-model="password"
            class="field-input"
            :password="!showPass"
            placeholder="密码"
            placeholder-class="ph"
            @focus="focusField = 'pass'"
            @blur="focusField = ''"
          />
          <view class="field-suffix" @click="showPass = !showPass">
            <svg
              v-if="showPass"
              class="eye-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#a0aec0"
              stroke-width="1.75"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
            <svg
              v-else
              class="eye-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#a0aec0"
              stroke-width="1.75"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
              <circle cx="12" cy="12" r="3" />
              <line x1="4" y1="5" x2="20" y2="19" />
            </svg>
          </view>
        </view>

        <view v-if="showCaptcha" class="captcha-row">
          <view class="field captcha-input" :class="{ focused: focusField === 'captcha' }">
            <view class="field-prefix">
              <svg
                class="field-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="#a0aec0"
                stroke-width="1.75"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="8.5" cy="12.5" r="4.5" />
                <path d="M12 12h9" />
                <path d="M18 12v3" />
                <path d="M15 12v3" />
              </svg>
            </view>
            <input
              v-model="captchaAnswer"
              class="field-input"
              placeholder="请输入验证码"
              placeholder-class="ph"
              maxlength="6"
              @focus="focusField = 'captcha'"
              @blur="focusField = ''"
              @confirm="submit"
            />
          </view>
            <view
            v-if="captchaImg"
            class="captcha-img-wrap cd-pressable"
            @click="refreshCaptcha"
          >
            <image
              :src="captchaImg"
              class="captcha-img"
              mode="aspectFit"
            />
          </view>
          <view v-else class="captcha-skeleton" />
        </view>

        <view
          class="login-btn cd-pressable"
          :class="{ loading }"
          @click="submit"
        >
          <text class="login-btn-text">
            {{ loading ? (mode === 'login' ? '登录中...' : '注册中...') : (mode === 'login' ? '登 录' : '注 册') }}
          </text>
        </view>
      </view>
    </view>

    <MobileConfirmDialog
      v-model:show="pendingDialogVisible"
      :title="pendingDialogTitle"
      :message="pendingDialogMessage"
      confirm-text="我知道了"
      alert-only
      tone="info"
      @confirm="onPendingDialogConfirm"
    />
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bg-base {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 120% 100% at 50% 45%,
      #eef2f8 0%,
      #dde5f0 40%,
      #c8d6e8 70%,
      #b8c9df 100%
    );
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(100, 130, 180, 0.09) 1rpx, transparent 1rpx),
    linear-gradient(90deg, rgba(100, 130, 180, 0.09) 1rpx, transparent 1rpx);
  background-size: 56rpx 56rpx;
}

.center-wrap {
  position: relative;
  z-index: 1;
  width: 86%;
  max-width: 640rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: calc(var(--status-bar-height, 0rpx) + 40rpx);
}

.logo-float {
  position: relative;
  z-index: 2;
  margin-bottom: -44rpx;
}

.logo-box {
  width: 112rpx;
  height: 112rpx;
  border-radius: 30rpx;
  background: linear-gradient(145deg, #1e293b 0%, #0f172a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 16rpx 48rpx rgba(15, 23, 42, 0.35),
    0 4rpx 12rpx rgba(15, 23, 42, 0.2),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.12);
}

.card {
  width: 100%;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20rpx);
  border-radius: 36rpx;
  padding: 80rpx 52rpx 52rpx;
  box-shadow:
    0 20rpx 60rpx rgba(100, 130, 180, 0.18),
    0 4rpx 16rpx rgba(100, 130, 180, 0.1),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.95);
  border: 1rpx solid rgba(255, 255, 255, 0.7);
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.card-title {
  display: block;
  text-align: center;
  font-size: 40rpx;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: 0.5rpx;
  margin-bottom: 4rpx;
}

.field {
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 28rpx;
  border-radius: 999rpx;
  background: #f4f7fb;
  border: 2rpx solid #e8eef6;
  transition: all 0.2s;
  gap: 16rpx;
  box-sizing: border-box;

  &.focused {
    background: #fff;
    border-color: #94b4d8;
    box-shadow: 0 0 0 6rpx rgba(100, 150, 210, 0.12);
  }
}

.field-prefix,
.field-suffix {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.field-suffix {
  padding: 8rpx;
}

.eye-icon,
.field-icon {
  width: 38rpx;
  height: 38rpx;
  display: block;
}

.field-input {
  flex: 1;
  height: 96rpx;
  font-size: 30rpx;
  color: #0f172a;
  font-weight: 500;
}

.ph {
  color: #b0bdc9;
  font-size: 28rpx;
}

.captcha-row {
  display: flex;
  gap: 16rpx;
  align-items: center;
}

.captcha-input {
  flex: 1;
  min-width: 0;
}

.captcha-img-wrap {
  flex-shrink: 0;
  width: 200rpx;
  height: 96rpx;
  border-radius: 999rpx;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.2);
}

.captcha-img {
  width: 100%;
  height: 100%;
}

.captcha-skeleton {
  flex-shrink: 0;
  width: 200rpx;
  height: 96rpx;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%);
  background-size: 200% 100%;
  animation: captcha-shimmer 1.2s ease-in-out infinite;
}

@keyframes captcha-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.login-btn {
  margin-top: 8rpx;
  width: 100%;
  height: 100rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  background: #0f172a;
  box-shadow:
    0 12rpx 36rpx rgba(15, 23, 42, 0.3),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.1);
  position: relative;
  overflow: hidden;
  transition: all 0.18s;
  box-sizing: border-box;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.08) 0%, transparent 55%);
    pointer-events: none;
  }

  &:active {
    transform: scale(0.97);
    box-shadow: 0 6rpx 18rpx rgba(15, 23, 42, 0.25);
  }

  &.loading {
    opacity: 0.72;
    pointer-events: none;
  }
}

.login-btn-text {
  font-size: 32rpx;
  font-weight: 700;
  color: #fff;
  letter-spacing: 4rpx;
}

.auth-tabs {
  display: flex;
  justify-content: center;
  gap: 64rpx;
  margin-bottom: 12rpx;
  border-bottom: 1rpx solid rgba(148, 163, 184, 0.1);
  padding-bottom: 16rpx;
}

.auth-tab {
  position: relative;
  padding: 8rpx 16rpx;
  
  .auth-tab-text {
    font-size: 32rpx;
    font-weight: 600;
    color: #64748b;
    transition: all 0.25s ease;
  }

  &.active {
    .auth-tab-text {
      color: #0f172a;
      font-weight: 800;
    }
  }
}

.auth-tab-line {
  position: absolute;
  bottom: -18rpx;
  left: 12%;
  width: 76%;
  height: 4rpx;
  border-radius: 99rpx;
  background: linear-gradient(90deg, #4f7cff 0%, #0f172a 100%);
  transform: scaleX(0);
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.auth-tab.active .auth-tab-line {
  transform: scaleX(1);
}
</style>
