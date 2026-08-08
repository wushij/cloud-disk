<script setup lang="ts">
import { ref } from 'vue'
import { authApi } from '@/api'

const props = defineProps<{
  email: string
  scene: 'login' | 'register' | 'resetpwd' | 'bind'
}>()

const loading = ref(false)
const countdown = ref(0)

async function sendCode() {
  if (loading.value || countdown.value > 0) return

  const em = props.email ? props.email.trim() : ''
  if (!em) {
    uni.showToast({ title: '请先输入有效的邮箱地址', icon: 'none' })
    return
  }
  if (!em.match(/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/)) {
    uni.showToast({ title: '邮箱格式不正确', icon: 'none' })
    return
  }

  loading.value = true
  try {
    await authApi.sendEmailCode({ email: em, scene: props.scene })
    uni.showToast({ title: '验证码已发送，请查收', icon: 'none' })
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (e: any) {
    uni.showToast({ title: e?.message || '发送失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <view
    class="mobile-send-btn cd-pressable"
    :class="{ disabled: loading || countdown > 0 }"
    @click="sendCode"
  >
    {{ countdown > 0 ? `${countdown}s 后重发` : (loading ? '发送中...' : '发送验证码') }}
  </view>
</template>

<style scoped>
.mobile-send-btn {
  font-size: 24rpx;
  font-weight: 600;
  color: #ffffff;
  background: #0f172a;
  border: none !important;
  outline: none !important;
  box-shadow: none !important;
  border-radius: 999rpx;
  padding: 0 28rpx;
  height: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
  flex-shrink: 0;
  box-sizing: border-box;
  transition: all 0.2s;
}

.mobile-send-btn::after {
  border: none !important;
}

.mobile-send-btn.disabled {
  color: #94a3b8;
  background: #e2e8f0;
  pointer-events: none;
}
</style>
