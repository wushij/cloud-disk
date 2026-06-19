<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const username = ref('')
const password = ref('')
const loading = ref(false)
const showPass = ref(false)
const focusField = ref('')

async function submit() {
  if (!username.value.trim() || !password.value) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    uni.reLaunch({ url: '/pages/disk/index' })
  } catch (e) {
    const msg = e instanceof Error ? e.message : '登录失败'
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="page">
    <!-- 背景网格 -->
    <view class="bg-base">
      <view class="bg-grid" />
    </view>

    <view class="center-wrap">
      <!-- Logo 图标悬浮在卡片上方 -->
      <view class="logo-float">
        <view class="logo-box">
          <svg width="34" height="34" viewBox="0 0 1024 1024">
            <path fill="#fff" d="M544 864V672h128L512 480 352 672h128v192H320v-1.6c-5.376.32-10.496 1.6-16 1.6A240 240 0 0 1 64 624c0-123.136 93.12-223.488 212.608-237.248A239.81 239.81 0 0 1 512 192a239.87 239.87 0 0 1 235.456 194.752c119.488 13.76 212.48 114.112 212.48 237.248a240 240 0 0 1-240 240c-5.376 0-10.56-1.28-16-1.6v1.6z" />
          </svg>
        </view>
      </view>

      <!-- 登录卡片 -->
      <view class="card">
        <text class="card-title">CloudDisk Pro</text>

        <!-- 用户名 -->
        <view class="field" :class="{ focused: focusField === 'user' }">
          <view class="field-prefix">
            <u-icon name="account" size="19" color="#a0aec0" />
          </view>
          <input
            v-model="username"
            class="field-input"
            placeholder="账号"
            placeholder-class="ph"
            @focus="focusField = 'user'"
            @blur="focusField = ''"
          />
        </view>

        <!-- 密码 -->
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
            <u-icon :name="showPass ? 'eye-fill' : 'eye-off'" size="19" color="#a0aec0" />
          </view>
        </view>

        <!-- 登录按钮 -->
        <button
          class="login-btn"
          :loading="loading"
          :disabled="loading"
          @click="submit"
        >
          <text class="login-btn-text">登录</text>
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
/* ===== 页面 ===== */
.page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ===== 背景 ===== */
.bg-base {
  position: absolute;
  inset: 0;
  /* 中间亮、四角暗的浅蓝灰渐变，与参考网站一致 */
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
  /* 细网格线 */
  background-image:
    linear-gradient(rgba(100, 130, 180, 0.09) 1rpx, transparent 1rpx),
    linear-gradient(90deg, rgba(100, 130, 180, 0.09) 1rpx, transparent 1rpx);
  background-size: 56rpx 56rpx;
}

/* ===== 内容居中 ===== */
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

/* ===== Logo 浮层 ===== */
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
    inset 0 1rpx 0 rgba(255,255,255,0.12);
}

/* ===== 卡片 ===== */
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
    inset 0 1rpx 0 rgba(255,255,255,0.95);
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
  margin-bottom: 8rpx;
}

/* ===== 表单字段 ===== */
.field {
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 28rpx;
  border-radius: 22rpx;
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

.field-prefix {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.field-suffix {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 8rpx;
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

/* ===== 登录按钮 ===== */
.login-btn {
  margin-top: 8rpx;
  width: 100%;
  height: 100rpx;
  line-height: 100rpx;
  border-radius: 999rpx;
  background: #0f172a;
  border: none;
  box-shadow:
    0 12rpx 36rpx rgba(15, 23, 42, 0.3),
    inset 0 1rpx 0 rgba(255,255,255,0.1);
  position: relative;
  overflow: hidden;
  transition: all 0.18s;
  box-sizing: border-box;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(255,255,255,0.08) 0%, transparent 55%);
    pointer-events: none;
  }

  &:active {
    transform: scale(0.97);
    box-shadow: 0 6rpx 18rpx rgba(15, 23, 42, 0.25);
  }

  &[disabled] {
    opacity: 0.6;
  }
}

.login-btn-text {
  font-size: 32rpx;
  font-weight: 700;
  color: #fff;
  letter-spacing: 4rpx;
}
</style>
