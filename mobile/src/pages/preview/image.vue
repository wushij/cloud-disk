<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const url = ref('')
const name = ref('')
const scale = ref(1)
const showUI = ref(true)

onLoad((query) => {
  url.value = decodeURIComponent((query?.url as string) || '')
  name.value = decodeURIComponent((query?.name as string) || '图片预览')
})

function goBack() {
  uni.navigateBack()
}

function handleDoubleClick() {
  if (scale.value > 1) {
    scale.value = 1
  } else {
    scale.value = 2.5
  }
}

function toggleUI() {
  showUI.value = !showUI.value
}
</script>

<template>
  <view class="page">
    <view class="top-bar" :class="{ 'top-bar-hidden': !showUI }">
      <view class="back" @click="goBack">
        <u-icon name="arrow-left" size="20" color="#fff" />
      </view>
      <text class="title">{{ name }}</text>
    </view>
    <view class="preview-container">
      <movable-area class="movable-area" scale-area>
        <movable-view
          class="movable-view"
          direction="all"
          scale
          :scale-min="0.8"
          :scale-max="4"
          :scale-value="scale"
          @dblclick="handleDoubleClick"
        >
          <image
            :src="url"
            class="preview-image"
            mode="aspectFit"
            @click="toggleUI"
          />
        </movable-view>
      </movable-area>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  background: #000;
  overflow: hidden;
}

.top-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: calc(var(--status-bar-height, 0px) + 20rpx) 24rpx 20rpx;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.75) 0%, transparent 100%);
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.top-bar-hidden {
  transform: translateY(-100%);
  opacity: 0;
  pointer-events: none;
}

.back {
  width: 60rpx;
  height: 60rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(10rpx);
  display: flex;
  align-items: center;
  justify-content: center;
}

.title {
  flex: 1;
  font-size: 28rpx;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.5);
}

.preview-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #09090b;
}

.movable-area {
  width: 100%;
  height: 100%;
}

.movable-view {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-image {
  width: 100%;
  height: 100%;
}
</style>
