<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const url = ref('')
const name = ref('')

onLoad((query) => {
  url.value = decodeURIComponent((query?.url as string) || '')
  name.value = decodeURIComponent((query?.name as string) || '视频播放')
})
</script>

<template>
  <view class="page">
    <view class="top-bar">
      <text class="title">{{ name }}</text>
    </view>
    <video :src="url" class="player" controls autoplay object-fit="contain" :show-center-play-btn="true" />
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  background: #000;
}

.top-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: calc(var(--status-bar-height, 0px) + 20rpx) 24rpx 20rpx;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.65) 0%, transparent 100%);
}

.title {
  max-width: 100%;
  font-size: 28rpx;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player {
  width: 100%;
  height: 100vh;
}
</style>
