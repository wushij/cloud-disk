<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { request, applySecurityConfig } from '@/api/http'
import MobileHeader from '@/components/MobileHeader.vue'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)

const timestampEnabled = ref(true)
const nonceEnabled = ref(true)
const sm3SignEnabled = ref(false)
const sm4EncryptEnabled = ref(false)

async function loadConfig() {
  loading.value = true
  try {
    const data = await request<Record<string, any>>({ url: '/api/admin/security/config' })
    if (data) {
      timestampEnabled.value = Boolean(data.timestampEnabled)
      nonceEnabled.value = Boolean(data.nonceEnabled)
      sm3SignEnabled.value = Boolean(data.sm3SignEnabled || data.sm2SignEnabled)
      sm4EncryptEnabled.value = Boolean(data.sm4EncryptEnabled)
    }
  } catch (err: any) {
    uni.showToast({ title: err?.message || '获取安全配置失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function saveConfig() {
  saving.value = true
  try {
    const res = await request<Record<string, any>>({
      url: '/api/admin/security/config',
      method: 'PUT',
      data: {
        timestampEnabled: timestampEnabled.value,
        nonceEnabled: nonceEnabled.value,
        sm3SignEnabled: sm3SignEnabled.value,
        sm4EncryptEnabled: sm4EncryptEnabled.value,
      }
    })
    applySecurityConfig(res)
    uni.showToast({ title: '保存成功', icon: 'success' })
  } catch (err: any) {
    uni.showToast({ title: err?.message || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

onShow(() => {
  if (!auth.isAdmin) {
    uni.showToast({ title: '需要管理员权限', icon: 'none' })
    uni.navigateBack().catch(() => uni.switchTab({ url: '/pages/profile/index' }))
    return
  }
  loadConfig()
})
</script>

<template>
  <view class="security-page">
    <MobileHeader title="接口安全与国密" caption="全链路请求防护与国密加密配置" show-back />

    <view class="container">
      <view class="section-label">安全策略选项</view>

      <view class="menu-group-card">
        <!-- 1. 时间戳有效性校验 -->
        <view class="setting-item">
          <view class="item-icon-box blue">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z" fill="#2563eb"/>
            </svg>
          </view>
          <view class="item-info">
            <text class="item-title">时间戳有效期校验</text>
            <text class="item-desc">限制请求 ±5 分钟时间窗口，识别超时过期请求</text>
          </view>
          <switch
            :checked="timestampEnabled"
            color="#010710"
            @change="(e) => timestampEnabled = e.detail.value"
          />
        </view>

        <!-- 2. Nonce 随机数防重放 -->
        <view class="setting-item">
          <view class="item-icon-box cyan">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-2 16l-4-4 1.41-1.41L10 14.17l6.59-6.59L18 9l-8 8z" fill="#0891b2"/>
            </svg>
          </view>
          <view class="item-info">
            <text class="item-title">Nonce 随机数防重放</text>
            <text class="item-desc">结合 Redis 唯一标识检验，拦截二次重放攻击</text>
          </view>
          <switch
            :checked="nonceEnabled"
            color="#010710"
            @change="(e) => nonceEnabled = e.detail.value"
          />
        </view>

        <!-- 3. 国密 HMAC-SM3 数字签名 -->
        <view class="setting-item">
          <view class="item-icon-box violet">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z" fill="#7c3aed"/>
            </svg>
          </view>
          <view class="item-info">
            <text class="item-title">国密 HMAC-SM3 数字签名</text>
            <text class="item-desc">请求参数生成 SM3 摘要签名，防数据非法篡改</text>
          </view>
          <switch
            :checked="sm3SignEnabled"
            color="#010710"
            @change="(e) => sm3SignEnabled = e.detail.value"
          />
        </view>

        <!-- 4. 国密 SM4-CBC 接口加解密 -->
        <view class="setting-item">
          <view class="item-icon-box emerald">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z" fill="#059669"/>
            </svg>
          </view>
          <view class="item-info">
            <text class="item-title">国密 SM4-CBC 接口加解密</text>
            <text class="item-desc">全链路 Body 报文端到端对称加密传输</text>
          </view>
          <switch
            :checked="sm4EncryptEnabled"
            color="#010710"
            @change="(e) => sm4EncryptEnabled = e.detail.value"
          />
        </view>
      </view>

      <!-- 提示卡片 -->
      <view class="tip-card">
        <view class="tip-header">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z" fill="#2563eb"/>
          </svg>
          <text class="tip-title">安全同步与生效说明</text>
        </view>
        <text class="tip-desc">
          开关配置保存后，前端/移动端将通过 session-sign-init 自动协商签名密钥，与后端 Servlet Filter 实时同步生效。
        </text>
      </view>

      <!-- 保存按钮 -->
      <view
        class="btn-save cd-pressable"
        :class="{ loading: saving }"
        @click="saveConfig"
      >
        <text class="btn-text">{{ saving ? '应用安全配置中...' : '保存并应用安全设置' }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.security-page {
  min-height: 100vh;
  background: var(--cd-bg);
  padding-bottom: 60rpx;
}

.container {
  padding: 24rpx;
}

.section-label {
  padding: 8rpx 12rpx 16rpx;
  font-size: 24rpx;
  font-weight: 700;
  color: var(--cd-text-muted);
  text-transform: uppercase;
  letter-spacing: 2rpx;
}

.menu-group-card {
  background: var(--cd-bg-card);
  border-radius: 32rpx;
  border: 1rpx solid var(--cd-border-light);
  box-shadow: var(--cd-shadow-card);
  overflow: hidden;
}

.setting-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 28rpx 28rpx;
  border-bottom: 1rpx solid var(--cd-border-light);

  &:last-child {
    border-bottom: none;
  }
}

.item-icon-box {
  width: 72rpx;
  height: 72rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &.blue { background: rgba(37, 99, 235, 0.08); }
  &.cyan { background: rgba(8, 145, 178, 0.08); }
  &.violet { background: rgba(124, 58, 237, 0.08); }
  &.emerald { background: rgba(5, 150, 105, 0.08); }
}

.item-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.item-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--cd-text);
  line-height: 1.4;
}

.item-desc {
  font-size: 22rpx;
  color: var(--cd-text-muted);
  line-height: 1.4;
}

/* 提示卡片 */
.tip-card {
  margin-top: 28rpx;
  padding: 28rpx 32rpx;
  border-radius: 28rpx;
  background: rgba(37, 99, 235, 0.04);
  border: 1rpx solid rgba(37, 99, 235, 0.12);
}

.tip-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 10rpx;
}

.tip-title {
  font-size: 26rpx;
  font-weight: 700;
  color: #2563eb;
}

.tip-desc {
  font-size: 22rpx;
  color: var(--cd-text-secondary);
  line-height: 1.6;
  display: block;
}

/* 保存按钮 */
.btn-save {
  margin-top: 40rpx;
  height: 96rpx;
  border-radius: 28rpx;
  background: var(--cd-primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10rpx 28rpx rgba(1, 7, 16, 0.2);
  transition: all var(--cd-transition-bounce);

  &:active {
    transform: scale(0.98);
    box-shadow: 0 4rpx 12rpx rgba(1, 7, 16, 0.12);
  }

  &.loading {
    opacity: 0.75;
  }
}

.btn-text {
  font-size: 30rpx;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 1rpx;
}
</style>
