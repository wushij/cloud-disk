<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{ src: string }>()

const text = ref('')
const loading = ref(true)
const error = ref('')

async function loadText(url: string) {
  loading.value = true
  error.value = ''
  text.value = ''
  try {
    const res = await fetch(url)
    if (!res.ok) throw new Error('加载失败')
    text.value = await res.text()
  } catch {
    error.value = '文本加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.src,
  (url) => {
    if (url) void loadText(url)
  },
  { immediate: true }
)
</script>

<template>
  <div v-loading="loading" class="cd-text-preview-wrap">
    <pre v-if="text" class="cd-text-preview">{{ text }}</pre>
    <el-empty v-else-if="error" :description="error" />
  </div>
</template>

<style scoped>
.cd-text-preview-wrap {
  min-height: 240px;
  max-height: 75vh;
  overflow: auto;
  border-radius: var(--cd-radius);
  background: var(--cd-bg);
  border: 1px solid var(--cd-border-light);
}

.cd-text-preview {
  margin: 0;
  padding: 16px 20px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  color: var(--cd-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
