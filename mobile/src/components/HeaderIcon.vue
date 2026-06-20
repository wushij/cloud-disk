<script setup lang="ts">
defineProps<{
  type: 'cloud' | 'recycle' | 'share' | 'team' | 'bell'
  /** 深色渐变头部 */
  light?: boolean
}>()
</script>

<template>
  <view class="header-icon" :class="{ light }">
    <!-- 云盘：简洁实心云朵 -->
    <svg v-if="type === 'cloud'" class="header-icon-svg" viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M19.35 10.04A7.49 7.49 0 0 0 12 4C9.11 4 6.6 5.64 5.35 8.04A5.994 5.994 0 0 0 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z"
      />
    </svg>

    <!-- 分享：三节点连线 -->
    <svg v-else-if="type === 'share'" class="header-icon-svg" viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z"
      />
    </svg>

    <!-- 回送站 -->
    <svg v-else-if="type === 'recycle'" class="header-icon-svg" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
    </svg>

    <!-- 团队 -->
    <svg v-else-if="type === 'team'" class="header-icon-svg" viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"
      />
    </svg>

    <!-- 消息通知：铃铛 -->
    <svg v-else-if="type === 'bell'" class="header-icon-svg" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1-.89 2-2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"/>
    </svg>
  </view>
</template>

<style scoped lang="scss">
.header-icon {
  flex-shrink: 0;
  width: 44rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-icon-svg {
  width: 44rpx;
  height: 44rpx;
  display: block;
}

.header-icon path {
  fill: var(--cd-primary);
}

.header-icon.light path {
  fill: rgba(255, 255, 255, 0.62);
}
</style>
