<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { toUserMessage } from '@/utils/error'

const props = defineProps<{
  documentServerUrl: string
  config: Record<string, unknown>
}>()

const editorId = `oo-editor-${Math.random().toString(36).slice(2, 10)}`
const loading = ref(true)
const error = ref('')

type DocEditor = { destroyEditor?: () => void }
type DocsApi = { DocEditor: new (id: string, cfg: Record<string, unknown>) => DocEditor }

let docEditor: DocEditor | null = null

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
  loading.value = true
  error.value = ''
  docEditor?.destroyEditor?.()
  docEditor = null
  if (!props.documentServerUrl || !props.config) {
    loading.value = false
    error.value = '在线文档配置无效'
    return
  }
  try {
    const base = props.documentServerUrl.replace(/\/$/, '')
    await loadScript(`${base}/web-apps/apps/api/documents/api.js`)
    const DocsAPI = (window as Window & { DocsAPI?: DocsApi }).DocsAPI
    if (!DocsAPI) {
      throw new Error('文档编辑器未就绪')
    }
    docEditor = new DocsAPI.DocEditor(editorId, props.config)
  } catch (e: unknown) {
    error.value = toUserMessage(e instanceof Error ? e.message : '', '文档编辑器初始化失败')
  } finally {
    loading.value = false
  }
}

onMounted(mountEditor)
watch(() => [props.documentServerUrl, props.config], mountEditor, { deep: true })
onBeforeUnmount(() => docEditor?.destroyEditor?.())
</script>

<template>
  <div class="oo-wrap">
    <div v-if="loading" class="oo-status">正在加载在线文档编辑器…</div>
    <div v-else-if="error" class="oo-status oo-error">{{ error }}</div>
    <div :id="editorId" class="oo-editor" />
  </div>
</template>

<style scoped>
.oo-wrap {
  width: 100%;
  min-height: 70vh;
  position: relative;
}
.oo-editor {
  width: 100%;
  height: 70vh;
}
.oo-status {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
}
.oo-error {
  color: var(--el-color-danger);
}
</style>
