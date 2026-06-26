<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureMediaToken } from '@/utils/mediaToken'
import { fileApiUrl } from '@/api/http'

const url = ref('')
const name = ref('')
const scale = ref(1)
const showUI = ref(true)
const loading = ref(true)
const loadError = ref('')

onLoad(async (query) => {
  name.value = decodeURIComponent((query?.name as string) || '图片预览')
  const fileId = Number(query?.fileId || 0)
  try {
    await ensureMediaToken()
    if (fileId > 0) {
      url.value = fileApiUrl(`/api/files/${fileId}/preview`)
    } else {
      url.value = decodeURIComponent((query?.url as string) || '')
    }
  } catch {
    loadError.value = '图片加载失败，请返回后重试'
    loading.value = false
  }
})

function handleDoubleClick() {
  scale.value = scale.value > 1 ? 1 : 2.5
}

function toggleUI() {
  showUI.value = !showUI.value
}

function onImageLoad() {
  loading.value = false
  loadError.value = ''
}

function onImageError() {
  loading.value = false
  loadError.value = '图片加载失败，请检查网络后重试'
}
</script>

<template>
  <view class="page">
    <view class="top-bar" :class="{ 'top-bar-hidden': !showUI }">
      <text class="title">{{ name }}</text>
    </view>
    <view class="preview-container">
      <view v-if="loading && !loadError" class="loading-tip">
        <text>加载中...</text>
      </view>
      <view v-if="loadError" class="error-tip">
        <text>{{ loadError }}</text>
      </view>
      <movable-area v-if="url && !loadError" class="movable-area" scale-area>
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
            @load="onImageLoad"
            @error="onImageError"
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
  justify-content: center;
  padding: calc(var(--status-bar-height, 0px) + 20rpx) 24rpx 20rpx;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.75) 0%, transparent 100%);
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.top-bar-hidden {
  transform: translateY(-100%);
  opacity: 0;
  pointer-events: none;
}

.title {
  max-width: 100%;
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

.loading-tip,
.error-tip {
  position: absolute;
  left: 48rpx;
  right: 48rpx;
  text-align: center;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.82);
}

.error-tip {
  color: #fecaca;
}
</style>
