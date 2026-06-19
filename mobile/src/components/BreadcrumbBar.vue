<script setup lang="ts">
defineProps<{
  crumbs: { id: number; name: string }[]
}>()

defineEmits<{
  (e: 'select', idx: number): void
}>()
</script>

<template>
  <scroll-view scroll-x class="crumb-scroll" :show-scrollbar="false">
    <view class="crumb-row">
      <view
        v-for="(crumb, idx) in crumbs"
        :key="crumb.id"
        class="crumb-chip"
        :class="{ active: idx === crumbs.length - 1 }"
        @click="$emit('select', idx)"
      >
        <text>{{ crumb.name }}</text>
        <u-icon v-if="idx < crumbs.length - 1" name="arrow-right" size="12" color="#94a3b8" />
      </view>
    </view>
  </scroll-view>
</template>

<style scoped lang="scss">
.crumb-scroll {
  width: 100%;
  white-space: nowrap;
}

.crumb-row {
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  padding: 2rpx 0;
}

.crumb-chip {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #f1f5f9;
  border: 1rpx solid var(--cd-border-light);
  font-size: 20rpx;
  color: var(--cd-text-secondary);
  font-weight: 500;
  transition: all var(--cd-transition-fast);
}

.crumb-chip:active {
  background: #e2e8f0;
}

.crumb-chip.active {
  background: var(--cd-primary-muted);
  border-color: rgba(1, 7, 16, 0.08);
  font-weight: 700;
  color: var(--cd-text);
}
</style>
