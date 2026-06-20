<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/api/http'

const url = ref('')
const name = ref('')
const text = ref('')
const loading = ref(true)
const error = ref('')

onLoad((query) => {
  url.value = decodeURIComponent((query?.url as string) || '')
  name.value = decodeURIComponent((query?.name as string) || '文本预览')
  void loadText()
})

async function loadText() {
  if (!url.value) {
    error.value = '缺少预览地址'
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    let apiPath = url.value
    if (/^https?:\/\//.test(apiPath)) {
      apiPath = apiPath.replace(/^https?:\/\/[^/]+/, '')
    }
    const data = await request<string>({
      url: apiPath,
      method: 'GET',
      header: { Accept: 'text/plain,*/*' }
    })
    text.value = typeof data === 'string' ? data : String(data ?? '')
  } catch {
    error.value = '文本加载失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="top-bar">
      <text class="title">{{ name }}</text>
    </view>
    <scroll-view scroll-y class="content">
      <view v-if="loading" class="state-box">
        <u-loading-icon size="28" color="var(--cd-primary)" />
      </view>
      <text v-else-if="error" class="error-text">{{ error }}</text>
      <text v-else selectable class="text-body">{{ text }}</text>
    </scroll-view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
}

.top-bar {
  padding: calc(var(--status-bar-height, 0px) + 20rpx) 24rpx 20rpx;
  background: #fff;
  border-bottom: 1rpx solid #e2e8f0;
}

.title {
  display: block;
  font-size: 28rpx;
  color: #0f172a;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  flex: 1;
  height: calc(100vh - 120rpx);
  padding: 24rpx;
  box-sizing: border-box;
}

.text-body {
  display: block;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 26rpx;
  line-height: 1.7;
  color: #1e293b;
  white-space: pre-wrap;
  word-break: break-word;
}

.state-box {
  display: flex;
  justify-content: center;
  padding: 80rpx 0;
}

.error-text {
  display: block;
  text-align: center;
  color: #94a3b8;
  padding: 80rpx 0;
}
</style>
