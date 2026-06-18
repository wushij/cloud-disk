<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { toUserMessage } from '@/utils/error'

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorker

const props = defineProps<{ src: string }>()
const canvasRef = ref<HTMLCanvasElement | null>(null)
const pageNum = ref(1)
const pageCount = ref(0)
const loading = ref(true)
const error = ref('')
const scale = ref(1.2)

let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null

async function renderPage(num: number) {
  if (!pdfDoc || !canvasRef.value) return
  const page = await pdfDoc.getPage(num)
  const viewport = page.getViewport({ scale: scale.value })
  const canvas = canvasRef.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  canvas.height = viewport.height
  canvas.width = viewport.width
  await page.render({ canvas, canvasContext: ctx, viewport }).promise
}

async function loadPdf() {
  loading.value = true
  error.value = ''
  pdfDoc = null
  try {
    pdfDoc = await pdfjsLib.getDocument({ url: props.src }).promise
    pageCount.value = pdfDoc.numPages
    pageNum.value = 1
    await renderPage(1)
  } catch (e: unknown) {
    error.value = toUserMessage(e instanceof Error ? e.message : '', 'PDF 加载失败')
  } finally {
    loading.value = false
  }
}

async function prevPage() {
  if (pageNum.value <= 1) return
  pageNum.value--
  await renderPage(pageNum.value)
}

async function nextPage() {
  if (!pdfDoc || pageNum.value >= pageCount.value) return
  pageNum.value++
  await renderPage(pageNum.value)
}

function zoomIn() {
  scale.value = Math.min(3, scale.value + 0.2)
  renderPage(pageNum.value)
}

function zoomOut() {
  scale.value = Math.max(0.4, scale.value - 0.2)
  renderPage(pageNum.value)
}

onMounted(loadPdf)
watch(() => props.src, loadPdf)
onBeforeUnmount(() => {
  pdfDoc?.cleanup()
})
</script>

<template>
  <div class="pdf-preview">
    <div v-if="loading" class="status">加载 PDF…</div>
    <div v-else-if="error" class="status error">{{ error }}</div>
    <template v-else>
      <div class="toolbar">
        <el-button size="small" :disabled="pageNum <= 1" @click="prevPage">上一页</el-button>
        <span>{{ pageNum }} / {{ pageCount }}</span>
        <el-button size="small" :disabled="pageNum >= pageCount" @click="nextPage">下一页</el-button>
        <el-divider direction="vertical" />
        <el-button size="small" @click="zoomOut">-</el-button>
        <span style="font-size:13px">{{ Math.round(scale * 100) }}%</span>
        <el-button size="small" @click="zoomIn">+</el-button>
      </div>
      <canvas ref="canvasRef" class="canvas" />
    </template>
  </div>
</template>

<style scoped>
.pdf-preview {
  width: 100%;
  text-align: center;
}
.toolbar {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.canvas {
  max-width: 100%;
  border: 1px solid #ebeef5;
}
.status {
  padding: 24px;
  color: #909399;
}
.error {
  color: #f56c6c;
}
</style>
