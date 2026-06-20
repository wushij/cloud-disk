<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { request } from '@/api/http'
import MobileTabBar from '@/components/MobileTabBar.vue'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'
import { fmtSize } from '@/utils/fileCover'
import { globalStorageUsage, updateStorageUsage } from '@/utils/sharedState'

const auth = useAuthStore()
const notifyStore = useNotificationStore()
const usage = globalStorageUsage

const unreadCount = computed(() => notifyStore.unreadCount())

const storagePercent = computed(() => {
  if (!usage.value?.quotaBytes) return 0
  return Math.min(100, Math.round(((usage.value.usedBytes || 0) / usage.value.quotaBytes) * 100))
})

const avatarInitial = computed(() => (auth.displayName || 'U').charAt(0).toUpperCase())
const avatarLoadFailed = ref(false)
const aboutVisible = ref(false)
const logoutVisible = ref(false)

onShow(async () => {
  uni.hideTabBar({ animation: false }).catch(() => {})
  if (!auth.requireLogin()) return
  try {
    auth.fetchProfile().catch(() => {})
    notifyStore.loadFromApi().catch(() => {})
    const data = await request<{ usedBytes?: number; quotaBytes?: number }>({ url: '/api/storage/usage' })
    updateStorageUsage(data)
  } catch {
    /* Keep cached data */
  }
})

function onAvatarError() {
  avatarLoadFailed.value = true
}

function changeAvatar() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const tempFilePath = res.tempFilePaths[0]
      uni.showLoading({ title: '上传中...' })
      try {
        await auth.uploadAvatar(tempFilePath)
        avatarLoadFailed.value = false
        uni.showToast({ title: '修改成功', icon: 'success' })
      } catch (err) {
        uni.showToast({ title: '上传失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

function logout() {
  logoutVisible.value = true
}

function confirmLogout() {
  auth.logout()
  updateStorageUsage(null)
  uni.reLaunch({ url: '/pages/login/index' })
}

function goTeams() {
  uni.switchTab({ url: '/pages/teams/index' })
}

function goNotifications() {
  uni.navigateTo({ url: '/pages/notifications/index' })
}

function goTransfer() {
  uni.navigateTo({ url: '/pages/transfer/index' })
}

function goShares() {
  uni.switchTab({ url: '/pages/shares/index' })
}

function goRecycle() {
  uni.navigateTo({ url: '/pages/recycle/index' })
}

function showAbout() {
  aboutVisible.value = true
}
</script>

<template>
  <view class="page">
    <!-- 头部融合区 - 采用卡片两侧留白设计 -->
    <view class="hero-section-wrap">
      <view class="hero-section">
        <!-- 装饰光效（与 MobileHeader 相同的柔和径向渐变，移除生硬圆形） -->
        <view class="hero-glow" />

        <view class="hero-content">
          <!-- 头像 -->
          <view class="hero-avatar" @click="changeAvatar">
            <view class="avatar-ring">
              <image
                v-if="auth.hasAvatar && !avatarLoadFailed"
                :src="auth.avatarSrc"
                class="avatar-image"
                mode="aspectFill"
                @error="onAvatarError"
              />
              <view v-else class="avatar-inner">{{ avatarInitial }}</view>
              <!-- 头像编辑角标 overlay -->
              <view class="avatar-edit-badge">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                  <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a.996.996 0 0 0 0-1.41l-2.34-2.34a.996.996 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z" fill="#ffffff" />
                </svg>
              </view>
            </view>
          </view>

          <!-- 用户信息 -->
          <view class="hero-info">
            <text class="hero-name">{{ auth.displayName }}</text>
            <text class="hero-dot">·</text>
            <text class="hero-account">@{{ auth.username }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 存储卡片 -->
    <view v-if="usage" class="storage-card">
      <view class="storage-head">
        <text class="storage-title">存储空间</text>
        <text class="storage-percent">{{ storagePercent }}%</text>
      </view>

      <!-- 进度条 -->
      <view class="storage-progress">
        <view class="storage-track">
          <view
            class="storage-fill"
            :class="{ warn: storagePercent >= 80, danger: storagePercent >= 95 }"
            :style="{ width: `${storagePercent}%` }"
          />
        </view>
      </view>

      <!-- 容量信息 -->
      <view class="storage-detail">
        <view class="storage-item">
          <text class="storage-label">已使用</text>
          <text class="storage-value">{{ fmtSize(usage.usedBytes || 0) }}</text>
        </view>
        <view v-if="usage.quotaBytes" class="storage-item right">
          <text class="storage-label">总容量</text>
          <text class="storage-value">{{ fmtSize(usage.quotaBytes) }}</text>
        </view>
      </view>
    </view>

    <!-- 菜单分组 -->
    <view class="section-label">管理与设置</view>
    <view class="menu-group-card">
      <!-- 消息通知 -->
      <view class="menu-item cd-pressable" @click="goNotifications">
        <view class="menu-icon-box blue">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-2 1H8v-6c0-2.48 1.51-4.5 4-4.9.49-.08.99-.1 1.5-.1s1.01.02 1.5.1c2.49.4 4 2.42 4 4.9v6z" fill="#2563eb"/>
          </svg>
        </view>
        <view class="menu-body">
          <text class="menu-name">消息通知</text>
          <text class="menu-desc">团队邀请与系统消息</text>
        </view>
        <view v-if="unreadCount > 0" class="menu-badge">
          <text>{{ unreadCount > 99 ? '99+' : unreadCount }}</text>
        </view>
        <u-icon name="arrow-right" size="18" color="#cbd5e1" />
      </view>

      <!-- 传输列表 -->
      <view class="menu-item cd-pressable" @click="goTransfer">
        <view class="menu-icon-box orange">
          <!-- 📥 传输 -->
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z" fill="#f97316"/>
          </svg>
        </view>
        <view class="menu-body">
          <text class="menu-name">传输列表</text>
          <text class="menu-desc">查看上传与下载进度</text>
        </view>
        <u-icon name="arrow-right" size="18" color="#cbd5e1" />
      </view>

      <!-- 团队空间 -->
      <view class="menu-item cd-pressable" @click="goTeams">
        <view class="menu-icon-box teal">
          <!-- 👥 团队 -->
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z" fill="#0d9488"/>
          </svg>
        </view>
        <view class="menu-body">
          <text class="menu-name">团队空间</text>
          <text class="menu-desc">与团队成员共享和管理文件</text>
        </view>
        <u-icon name="arrow-right" size="18" color="#cbd5e1" />
      </view>

      <!-- 我的分享 -->
      <view class="menu-item cd-pressable" @click="goShares">
        <view class="menu-icon-box indigo">
          <!-- 🔗 分享 -->
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z" fill="#3b82f6"/>
          </svg>
        </view>
        <view class="menu-body">
          <text class="menu-name">我的分享</text>
          <text class="menu-desc">管理已创建的外链与分享记录</text>
        </view>
        <u-icon name="arrow-right" size="18" color="#cbd5e1" />
      </view>

      <!-- 回收站 -->
      <view class="menu-item cd-pressable" @click="goRecycle">
        <view class="menu-icon-box orange">
          <!-- 🗑 回收站 -->
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" fill="#f97316"/>
          </svg>
        </view>
        <view class="menu-body">
          <text class="menu-name">回收站</text>
          <text class="menu-desc">找回误删的个人或团队文件</text>
        </view>
        <u-icon name="arrow-right" size="18" color="#cbd5e1" />
      </view>

      <!-- 关于我们 -->
      <view class="menu-item cd-pressable" @click="showAbout">
        <view class="menu-icon-box violet">
          <!-- ℹ 信息 -->
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z" fill="#8b5cf6"/>
          </svg>
        </view>
        <view class="menu-body">
          <text class="menu-name">关于我们</text>
          <text class="menu-desc">查看系统版本信息及使用条款</text>
        </view>
        <u-icon name="arrow-right" size="18" color="#cbd5e1" />
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="logout-btn cd-pressable" @click="logout">
      <text>退出登录</text>
    </view>

    <MobileTabBar active="profile" />

    <MobileConfirmDialog
      v-model:show="logoutVisible"
      title="退出登录"
      message="确定退出当前账号？"
      confirm-text="退出"
      danger
      @confirm="confirmLogout"
    />

    <!-- 关于我们 -->
    <view v-if="aboutVisible" class="about-root" @touchmove.stop.prevent>
      <view class="about-mask" @click="aboutVisible = false" />
      <view class="about-panel cd-scale-in" @click.stop>
        <view class="about-logo">
          <svg width="36" height="36" viewBox="0 0 24 24" fill="none">
            <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z" fill="#ffffff"/>
          </svg>
        </view>
        <text class="about-name">CloudDisk Pro</text>
        <text class="about-version">版本 v1.2.0</text>
        <text class="about-desc">个人专属的高性能云端存储系统</text>
        <view class="about-btn cd-pressable" @click="aboutVisible = false">
          <text>知道了</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding-bottom: calc(var(--cd-tab-height) + env(safe-area-inset-bottom) + 30rpx);
  background: var(--cd-bg);
}

/* ==========================================
   1. 头部卡片区
   ========================================== */
.hero-section-wrap {
  padding: calc(var(--status-bar-height, 0rpx) + 20rpx) 24rpx 0;
  background: var(--cd-bg);
}

.hero-section {
  position: relative;
  overflow: hidden;
  padding: 44rpx 36rpx;
  border-radius: 32rpx;
  background: var(--cd-accent-surface);
  backdrop-filter: blur(24rpx);
  -webkit-backdrop-filter: blur(24rpx);
  border: 1rpx solid var(--cd-accent-border);
  box-shadow: var(--cd-accent-shadow);
}

.hero-glow {
  display: none;
}

.hero-content {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32rpx;
}

/* 头像 */
.hero-avatar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-ring {
  position: relative;
  padding: 6rpx;
  border-radius: 999rpx;
  background: #f1f5f9;
  border: 1rpx solid var(--cd-border);
  box-shadow: var(--cd-shadow-sm);
  transition: all var(--cd-transition);

  &:active {
    transform: scale(0.96);
    background: #e2e8f0;
  }
}

.avatar-inner {
  width: 130rpx;
  height: 130rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.95);
  color: #0f1a2e;
  font-size: 50rpx;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-image {
  width: 130rpx;
  height: 130rpx;
  border-radius: 999rpx;
  display: block;
}

.avatar-edit-badge {
  position: absolute;
  right: 2rpx;
  bottom: 2rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 999rpx;
  background: #1e293b;
  border: 2rpx solid #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 10rpx rgba(0,0,0,0.25);
}

/* 用户信息 */
.hero-info {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12rpx;
  text-align: left;
}

.hero-name {
  font-size: 38rpx;
  font-weight: 800;
  color: var(--cd-text);
  letter-spacing: 0.5rpx;
}

.hero-dot {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--cd-text-muted);
}

.hero-account {
  font-size: 26rpx;
  color: var(--cd-text-secondary);
  font-weight: 500;
}

/* ==========================================
   2. 存储空间卡片
   ========================================== */
.storage-card {
  position: relative;
  z-index: 2;
  margin: 24rpx 24rpx;
  padding: 32rpx;
  background: var(--cd-bg-card);
  border-radius: 32rpx;
  box-shadow: var(--cd-shadow-md);
  border: 1rpx solid var(--cd-border-light);
}

.storage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.storage-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--cd-text);
}

.storage-percent {
  font-size: 38rpx;
  font-weight: 800;
  color: #0f1a2e;
  font-variant-numeric: tabular-nums;
}

/* 进度条 */
.storage-progress {
  margin-bottom: 20rpx;
}

.storage-track {
  height: 16rpx;
  border-radius: 999rpx;
  background: #eef0f4;
  overflow: hidden;
}

.storage-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #010710 0%, #1e293b 100%);
  transition: width 0.5s ease;
  min-width: 4rpx;
}

.storage-fill.warn {
  background: linear-gradient(90deg, #f59e0b, #fbbf24);
}

.storage-fill.danger {
  background: linear-gradient(90deg, #ef4444, #f87171);
}

/* 容量详情 */
.storage-detail {
  display: flex;
}

.storage-item {
  flex: 1;
}

.storage-item.right {
  text-align: right;
}

.storage-label {
  display: block;
  font-size: 22rpx;
  color: var(--cd-text-muted);
  margin-bottom: 6rpx;
}

.storage-value {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--cd-text);
  font-variant-numeric: tabular-nums;
}

/* ==========================================
   3. 分组标题
   ========================================== */
.section-label {
  padding: 16rpx 28rpx 16rpx;
  font-size: 24rpx;
  font-weight: 700;
  color: var(--cd-text-muted);
  text-transform: uppercase;
  letter-spacing: 2rpx;
}

/* ==========================================
   4. 菜单卡片组
   ========================================== */
.menu-group-card {
  margin: 0 24rpx 24rpx;
  background: var(--cd-bg-card);
  border-radius: 32rpx;
  border: 1rpx solid var(--cd-border-light);
  box-shadow: var(--cd-shadow-card);
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 26rpx 28rpx;
  border-bottom: 1rpx solid var(--cd-border-light);
  transition: all var(--cd-transition-bounce);

  &:last-child {
    border-bottom: none;
  }

  &:active {
    background: var(--cd-bg-surface);
  }
}

.menu-icon-box {
  width: 68rpx;
  height: 68rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.menu-icon-box.teal {
  background: rgba(13, 148, 136, 0.08);
}

.menu-icon-box.blue {
  background: rgba(37, 99, 235, 0.08);
}

.menu-icon-box.indigo {
  background: rgba(59, 130, 246, 0.08);
}

.menu-icon-box.orange {
  background: rgba(249, 115, 22, 0.08);
}

.menu-icon-box.violet {
  background: rgba(139, 92, 246, 0.08);
}

.menu-body {
  flex: 1;
  min-width: 0;
}

.menu-name {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--cd-text);
}

.menu-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--cd-text-muted);
}

.menu-badge {
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background: #ef4444;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 8rpx;

  text {
    font-size: 20rpx;
    font-weight: 700;
    color: #fff;
    line-height: 1;
  }
}

/* ==========================================
   5. 退出登录按钮
   ========================================== */
.logout-btn {
  margin: 48rpx 24rpx;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  font-size: 30rpx;
  font-weight: 700;
  color: #ffffff;
  box-shadow: 0 8rpx 24rpx rgba(239, 68, 68, 0.3);
  transition: all var(--cd-transition-bounce);

  &:active {
    transform: scale(0.97);
    box-shadow: 0 4rpx 12rpx rgba(239, 68, 68, 0.2);
    opacity: 0.95;
  }
}

/* 关于弹窗 */
.about-root {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
}

.about-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(6rpx);
}

.about-panel {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 580rpx;
  padding: 48rpx 40rpx 36rpx;
  background: var(--cd-bg-card);
  border-radius: 32rpx;
  box-shadow:
    0 24rpx 64rpx rgba(15, 23, 42, 0.18),
    0 0 0 1rpx rgba(255, 255, 255, 0.6) inset;
  border: 1rpx solid var(--cd-border-light);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.about-logo {
  width: 96rpx;
  height: 96rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #010710 0%, #1e293b 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 32rpx rgba(1, 7, 16, 0.25);
  margin-bottom: 28rpx;
}

.about-name {
  font-size: 36rpx;
  font-weight: 800;
  color: var(--cd-text);
  letter-spacing: -0.5rpx;
}

.about-version {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: var(--cd-text-muted);
  font-weight: 500;
}

.about-desc {
  margin-top: 20rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: var(--cd-text-secondary);
  padding: 0 12rpx;
}

.about-btn {
  margin-top: 36rpx;
  width: 100%;
  height: 88rpx;
  border-radius: 999rpx;
  background: var(--cd-primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10rpx 28rpx rgba(1, 7, 16, 0.2);

  text {
    font-size: 28rpx;
    font-weight: 700;
    color: #fff;
  }
}

.about-btn:active {
  transform: scale(0.98);
  opacity: 0.95;
}
</style>
