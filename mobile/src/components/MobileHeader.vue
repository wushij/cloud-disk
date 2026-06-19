<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
  showBack?: boolean
  gradient?: boolean
  /** 图标类型：不同页面传入不同值，不传则不显示图标 */
  iconType?: 'cloud' | 'recycle' | 'share' | 'team'
  /** 图标下方的副标语，仅 cloud 类型默认显示 */
  caption?: string
}>()

defineEmits<{
  (e: 'back'): void
}>()
</script>

<template>
  <view class="m-header-wrap">
    <view class="m-header" :class="{ gradient }">

      <!-- 石墨主题装饰层 -->
      <view v-if="gradient" class="m-header-glow" />

      <!-- ===== 主行 ===== -->
      <view class="m-header-bar">

        <!-- 返回按钮 -->
        <view v-if="showBack" class="m-back-btn cd-pressable" @click="$emit('back')">
          <u-icon name="arrow-left" size="18" color="rgba(255,255,255,0.9)" />
        </view>

        <!-- 图标 + 文字 -->
        <view class="m-header-info">

          <!-- 图标区：仅 iconType 有值且非返回模式才显示 -->
          <view v-if="iconType && !showBack" class="m-icon-block">

            <!-- ☁ 云盘：纯云朵 -->
            <svg v-if="iconType === 'cloud'" width="36" height="36" viewBox="0 0 24 24" fill="none">
              <path d="M19.35 10.04A7.49 7.49 0 0 0 12 4C9.11 4 6.6 5.64 5.35 8.04A5.994 5.994 0 0 0 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z" fill="rgba(255,255,255,0.9)"/>
            </svg>

            <!-- 🗑 回收站 -->
            <svg v-else-if="iconType === 'recycle'" width="34" height="34" viewBox="0 0 24 24" fill="none">
              <path d="M9 3v1H4v2h1v13a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V6h1V4h-5V3H9zm0 5h2v9H9V8zm4 0h2v9h-2V8z" fill="rgba(255,255,255,0.9)"/>
            </svg>

            <!-- 🔗 分享 -->
            <svg v-else-if="iconType === 'share'" width="34" height="34" viewBox="0 0 24 24" fill="none">
              <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z" fill="rgba(255,255,255,0.9)"/>
            </svg>

            <!-- 👥 团队 -->
            <svg v-else-if="iconType === 'team'" width="36" height="36" viewBox="0 0 24 24" fill="none">
              <path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z" fill="rgba(255,255,255,0.9)"/>
            </svg>
          </view>

          <!-- 文字区 -->
          <view class="m-header-texts">
            <view class="m-header-title-row">
              <text class="m-title">{{ title }}</text>
              <view v-if="subtitle" class="m-subtitle-chip">
                <text class="m-subtitle-text">{{ subtitle }}</text>
              </view>
            </view>
            <!-- 副标语：优先用 caption prop，否则 cloud 类型显示默认文字 -->
            <text v-if="(caption || iconType === 'cloud') && gradient && !showBack" class="m-caption">
              {{ caption || '个人专属云端存储' }}
            </text>
          </view>
        </view>

        <!-- 右侧操作 -->
        <view class="m-header-actions">
          <slot name="right" />
        </view>
      </view>

      <!-- 附加区（面包屑 + 搜索） -->
      <view v-if="$slots.extra" class="m-header-extra">
        <slot name="extra" />
      </view>

    </view>
  </view>
</template>

<style scoped lang="scss">
/* ===== 外层 wrap：两侧留边让四角显圆角 ===== */
.m-header-wrap {
  padding: calc(var(--status-bar-height, 0rpx) + 20rpx) 24rpx 0;
  background: var(--cd-bg);
}

/* ===== 卡片主体 ===== */
.m-header {
  position: relative;
  overflow: hidden;
  padding: 28rpx 28rpx 32rpx;
  background: var(--cd-bg-card);
  border-radius: 32rpx;
  border: 1rpx solid var(--cd-border-light);
  box-shadow: var(--cd-shadow-md);
}

/* ===== 石墨渐变主题 ===== */
.m-header.gradient {
  background: linear-gradient(135deg, #010710 0%, #0f1a2e 55%, #1e293b 100%);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
  box-shadow:
    0 20rpx 56rpx rgba(1, 7, 16, 0.5),
    0 4rpx 12rpx rgba(1, 7, 16, 0.3),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.06);
}

/* 石墨光效 */
.m-header-glow {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(ellipse 60% 60% at 90% -10%, rgba(100, 116, 139, 0.22) 0%, transparent 60%),
    radial-gradient(ellipse 40% 50% at 0% 110%, rgba(15, 26, 46, 0.4) 0%, transparent 65%);
}

/* ===== 主行 flex ===== */
.m-header-bar {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 16rpx;
}

/* 图标 + 文字区块 */
.m-header-info {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 20rpx;
}

/* ===== 无背景无边框图标 — 直接浮在 header 上 ===== */
.m-icon-block {
  flex-shrink: 0;
  width: 52rpx;
  height: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 完全无背景、无边框、无阴影 */
  opacity: 0.88;
}

/* ===== 文字区块 ===== */
.m-header-texts {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.m-header-title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

/* 主标题 */
.m-title {
  font-size: 38rpx;
  font-weight: 800;
  color: var(--cd-text);
  letter-spacing: -0.5rpx;
  white-space: nowrap;
}

.gradient .m-title {
  color: #ffffff;
  text-shadow: 0 1rpx 12rpx rgba(0, 0, 0, 0.4);
}

/* 数量徽章 */
.m-subtitle-chip {
  padding: 3rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.1);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  flex-shrink: 0;
}

.m-subtitle-text {
  font-size: 19rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 0.2rpx;
}

/* 副标语 */
.m-caption {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.38);
  font-weight: 400;
  letter-spacing: 0.5rpx;
}

/* ===== 返回按钮 ===== */
.m-back-btn {
  width: 68rpx;
  height: 68rpx;
  flex-shrink: 0;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--cd-transition);

  &:active {
    background: rgba(255, 255, 255, 0.18);
    transform: scale(0.88);
  }
}

/* ===== 右侧操作区 ===== */
.m-header-actions {
  flex-shrink: 0;
}

/* ===== 附加区 ===== */
.m-header-extra {
  position: relative;
  z-index: 1;
  margin-top: 24rpx;
}
</style>
