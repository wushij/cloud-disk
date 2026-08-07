<script setup lang="ts">
import BrandMark from '@/components/BrandMark.vue'

defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  (e: 'update:show', val: boolean): void
}>()

function close() {
  emit('update:show', false)
}
</script>

<template>
  <view v-if="show" class="about-root" @touchmove.stop.prevent>
    <view class="about-mask" @click="close" />
    <view class="about-panel cd-scale-in" @click.stop>
      <view class="about-logo">
        <BrandMark size="48rpx" />
      </view>
      <text class="about-name">CloudDisk Pro</text>
      <text class="about-version">版本 v1.2.0</text>
      <text class="about-desc">个人专属的高性能云端存储系统</text>
      <view class="about-btn cd-pressable" @click="close">
        <text>知道了</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
@import '@/styles/profile-dialog.scss';
</style>
