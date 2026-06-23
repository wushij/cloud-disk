<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request, fileApiUrl, TOKEN_KEY } from '@/api/http'

const fileId = ref(0)
const name = ref('')
const loading = ref(true)
const error = ref('')

let docEditor: any = null

onLoad((query) => {
  fileId.value = Number(query?.id || 0)
  name.value = decodeURIComponent((query?.name as string) || '文档预览')
})

function loadScript(src: string): Promise<void> {
  const existing = document.querySelector(`script[src="${src}"]`)
  if (existing) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('在线文档服务脚本加载失败'))
    document.body.appendChild(script)
  })
}

async function mountEditor() {
  if (fileId.value <= 0) {
    error.value = '文件参数无效'
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    // 1. 获取 OnlyOffice 配置
    const data = await request<{ documentServerUrl: string; config: Record<string, any> }>({
      url: `/api/files/${fileId.value}/onlyoffice`,
      data: { mode: 'view' }
    })
    if (!data.documentServerUrl || !data.config) {
      throw new Error('在线文档配置无效')
    }
    
    // 2. 加载 OnlyOffice JS 脚本
    const base = data.documentServerUrl.replace(/\/$/, '')
    await loadScript(`${base}/web-apps/apps/api/documents/api.js`)
    
    const DocsAPI = (window as any).DocsAPI
    if (!DocsAPI) {
      throw new Error('在线文档编辑器未就绪')
    }
    
    // 3. 设置 OnlyOffice 嵌入预览类型
    const editorConfig = {
      width: '100%',
      height: '100%',
      ...data.config
    }
    editorConfig.type = 'embedded'
    editorConfig.embedded = {
      autostart: 'document',
      toolbarDocked: 'top'
    }
    
    // 4. 实例化编辑器
    docEditor = new DocsAPI.DocEditor('oo-editor', editorConfig)
  } catch (e: any) {
    error.value = e.message || '文档加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  mountEditor()
})

onBeforeUnmount(() => {
  if (docEditor && docEditor.destroyEditor) {
    docEditor.destroyEditor()
  }
})
</script>

<template>
  <view class="page">
    <view class="top-bar">
      <text class="title">{{ name }}</text>
    </view>
    <view class="content">
      <view v-if="loading" class="state-box">
        <u-loading-icon size="28" color="var(--cd-primary)" />
      </view>
      <view v-else-if="error" class="state-box error-text">{{ error }}</view>
      <div id="oo-editor" class="oo-editor" v-show="!loading && !error" />
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  height: 100vh;
  overflow: hidden;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
}

.top-bar {
  padding: calc(var(--status-bar-height, 0px) + 20rpx) 24rpx 20rpx;
  background: #fff;
  border-bottom: 1rpx solid #e2e8f0;
  flex-shrink: 0;
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
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.oo-editor {
  width: 100%;
  height: 100%;
  flex: 1;
  border: none;
}

.state-box {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 80rpx 0;
  font-size: 28rpx;
}

.error-text {
  color: #ef4444;
}
</style>
