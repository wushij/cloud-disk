<script setup lang="ts">
import { ref, watch } from 'vue'
import { request } from '@/api/http'
import MobileEmailCodeBtn from './MobileEmailCodeBtn.vue'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  (e: 'update:show', val: boolean): void
  (e: 'success', payload: { email: string; newPassword: string }): void
}>()

const email = ref('')
const code = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const focusField = ref('')

watch(() => props.show, (val) => {
  if (val) {
    email.value = ''
    code.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  }
})

function close() {
  emit('update:show', false)
}

async function handleReset() {
  const em = email.value.trim()
  const cd = code.value.trim()
  const np = newPassword.value
  const cp = confirmPassword.value

  if (!em || !cd || !np) {
    uni.showToast({ title: '请完整填写所需信息', icon: 'none' })
    return
  }
  if (np.length < 6) {
    uni.showToast({ title: '新密码不能少于 6 位', icon: 'none' })
    return
  }
  if (np !== cp) {
    uni.showToast({ title: '两次密码不一致', icon: 'none' })
    return
  }

  loading.value = true
  try {
    await request({
      url: '/api/auth/email/reset-password',
      method: 'POST',
      data: { email: em, code: cd, newPassword: np },
      skipAuth: true
    })
    uni.showToast({ title: '密码重置成功', icon: 'none' })
    emit('success', { email: em, newPassword: np })
    close()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '重置失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <view v-if="show" class="mobile-forgot-mask" @click.self="close">
    <view class="forgot-sheet">
      <text class="sheet-title">🔒 重置 / 找回密码</text>
      <text class="sheet-sub">验证邮箱后设置您的新登录密码</text>

      <view class="field-list">
        <view class="field" :class="{ focused: focusField === 'email' }">
          <u-icon name="email" size="19" color="#a0aec0" />
          <input
            v-model="email"
            class="field-input"
            placeholder="注册电子邮箱"
            placeholder-class="ph"
            @focus="focusField = 'email'"
            @blur="focusField = ''"
          />
        </view>

        <view class="field code-field" :class="{ focused: focusField === 'code' }">
          <svg class="field-icon" viewBox="0 0 24 24" fill="none" stroke="#a0aec0" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="7.5" cy="15.5" r="4.5" />
            <path d="M10.7 12.3L20 3" />
            <path d="M16 7l2 2" />
            <path d="M13 10l2 2" />
          </svg>
          <input
            v-model="code"
            class="field-input"
            placeholder="6 位验证码"
            placeholder-class="ph"
            maxlength="6"
            @focus="focusField = 'code'"
            @blur="focusField = ''"
          />
          <MobileEmailCodeBtn :email="email" scene="resetpwd" />
        </view>

        <view class="field" :class="{ focused: focusField === 'np' }">
          <u-icon name="lock" size="19" color="#a0aec0" />
          <input
            v-model="newPassword"
            class="field-input"
            password
            placeholder="新密码 (6-64位)"
            placeholder-class="ph"
            @focus="focusField = 'np'"
            @blur="focusField = ''"
          />
        </view>

        <view class="field" :class="{ focused: focusField === 'cp' }">
          <u-icon name="lock" size="19" color="#a0aec0" />
          <input
            v-model="confirmPassword"
            class="field-input"
            password
            placeholder="确认新密码"
            placeholder-class="ph"
            @focus="focusField = 'cp'"
            @blur="focusField = ''"
          />
        </view>
      </view>

      <view class="btn-row">
        <view class="cancel-btn cd-pressable" @click="close">取消</view>
        <view class="submit-btn cd-pressable" :class="{ loading }" @click="handleReset">
          <text>{{ loading ? '提交中...' : '确认重置' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.mobile-forgot-mask {
  position: fixed;
  inset: 0;
  z-index: 999;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(8rpx);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: calc(env(safe-area-inset-top) + 110rpx) 30rpx 30rpx;
  box-sizing: border-box;
}

.forgot-sheet {
  width: 100%;
  max-width: 640rpx;
  background: #ffffff;
  border-radius: 36rpx;
  padding: 52rpx 44rpx;
  box-sizing: border-box;
  box-shadow: 0 20rpx 60rpx rgba(15, 23, 42, 0.25);
  animation: popIn 0.22s ease-out;
}

.sheet-title {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: #0f172a;
  text-align: center;
  margin-bottom: 8rpx;
}

.sheet-sub {
  display: block;
  font-size: 24rpx;
  color: #64748b;
  text-align: center;
  margin-bottom: 32rpx;
}

.field-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.field {
  display: flex;
  align-items: center;
  height: 92rpx;
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

.code-field {
  padding-right: 12rpx;
}

.field-input {
  flex: 1;
  height: 92rpx;
  font-size: 28rpx;
  color: #0f172a;
}

.field-icon {
  width: 38rpx;
  height: 38rpx;
  flex-shrink: 0;
  display: block;
}

.ph {
  color: #b0bdc9;
  font-size: 26rpx;
}

.btn-row {
  display: flex;
  gap: 20rpx;
  margin-top: 36rpx;
}

.cancel-btn {
  flex: 1;
  height: 88rpx;
  border-radius: 999rpx;
  background: #f1f5f9;
  color: #64748b;
  font-size: 28rpx;
  font-weight: 600;
  border: none !important;
  outline: none !important;
  box-shadow: none !important;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;

  &:active {
    background: #e2e8f0;
  }
}

.submit-btn {
  flex: 1.5;
  height: 88rpx;
  border-radius: 999rpx;
  background: #0f172a;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
  border: none !important;
  outline: none !important;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;

  &.loading {
    opacity: 0.7;
    pointer-events: none;
  }

  &:active {
    transform: scale(0.98);
  }
}

@keyframes popIn {
  from { transform: scale(0.92); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
</style>
