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
      <u-icon :name="icon" size="52" color="var(--cd-primary)" />
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
