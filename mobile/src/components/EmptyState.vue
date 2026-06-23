<script setup lang="ts">
withDefaults(
  defineProps<{
    icon?: string
    title: string
    description?: string
  }>(),
  { icon: 'folder' }
)
</script>

<template>
  <view class="cd-empty">
    <view class="cd-empty-icon">
      <svg v-if="icon === 'download'" width="52" height="52" viewBox="0 0 24 24" fill="none" style="display: block;">
        <circle cx="12" cy="12" r="10" stroke="#111827" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" />
        <path d="M6.5 11L9 8.5L11.5 11M9 8.5V16.5" stroke="#111827" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M12.5 13.5L15 16L17.5 13.5M15 8V16" stroke="#111827" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <u-icon v-else :name="icon" size="52" color="var(--cd-primary)" />
    </view>
    <text class="cd-empty-title">{{ title }}</text>
    <text v-if="description" class="cd-empty-desc">{{ description }}</text>
    <view v-if="$slots.action" class="cd-empty-action">
      <slot name="action" />
    </view>
  </view>
</template>

<style scoped lang="scss">
.cd-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 48rpx;
  text-align: center;
}

.cd-empty-icon {
  width: 132rpx;
  height: 132rpx;
  border-radius: 36rpx;
  background: linear-gradient(145deg, rgba(1, 7, 16, 0.07), rgba(15, 26, 46, 0.03));
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
  box-shadow: inset 0 0 0 1rpx rgba(79, 70, 229, 0.06);
}

.cd-empty-title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--cd-text);
}

.cd-empty-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: var(--cd-text-muted);
  line-height: 1.6;
  max-width: 480rpx;
}

.cd-empty-action {
  margin-top: 28rpx;
}
</style>
