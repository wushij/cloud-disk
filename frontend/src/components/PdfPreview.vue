<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { toUserMessage } from '@/utils/error'

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorker

const props = defineProps<{ src: string }>()
const containerRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const pageNum = ref(1)
const pageCount = ref(0)
const loading = ref(true)
const error = ref('')
const scale = ref(1.0)
const zoomPercent = ref('100')

watch(scale, (newScale) => {
  zoomPercent.value = String(Math.round(newScale * 100))
}, { immediate: true })

function handleZoomPercentChange() {
  const val = parseInt(zoomPercent.value)
  if (!isNaN(val) && val >= 40 && val <= 300) {
    scale.value = val / 100
    renderPage(pageNum.value)
  } else {
    zoomPercent.value = String(Math.round(scale.value * 100))
  }
}

let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null
let resizeObserver: ResizeObserver | null = null

async function renderPage(num: number) {
  if (!pdfDoc || !canvasRef.value) return
  const page = await pdfDoc.getPage(num)
  const viewport = page.getViewport({ scale: scale.value })
  const canvas = canvasRef.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const dpr = window.devicePixelRatio || 1
  canvas.width = Math.floor(viewport.width * dpr)
  canvas.height = Math.floor(viewport.height * dpr)
  canvas.style.width = `${viewport.width}px`
  canvas.style.height = `${viewport.height}px`

  ctx.setTransform(1, 0, 0, 1, 0, 0)
  
  const renderContext = {
    canvas: canvas,
    canvasContext: ctx,
    viewport: viewport,
    transform: [dpr, 0, 0, dpr, 0, 0]
  }
  await page.render(renderContext).promise
}

async function fitPage() {
  if (!pdfDoc || !containerRef.value) return
  try {
    const page = await pdfDoc.getPage(pageNum.value)
    const unscaledViewport = page.getViewport({ scale: 1.0 })
    
    const padding = 48
    const availableWidth = containerRef.value.clientWidth - padding
    const availableHeight = containerRef.value.clientHeight - padding - 60 // approx toolbar height + gaps
    
    if (availableWidth <= 0 || availableHeight <= 0) return

    const scaleX = availableWidth / unscaledViewport.width
    const scaleY = availableHeight / unscaledViewport.height
    
    // Fit the page so it is completely visible in the viewport
    const optimalScale = Math.min(scaleX, scaleY)
    scale.value = Math.max(0.4, Math.min(3.0, optimalScale))
    await renderPage(pageNum.value)
  } catch (err) {
    console.error('Fit page calculation failed:', err)
  }
}

async function loadPdf() {
  loading.value = true
  error.value = ''
  pdfDoc = null
  try {
    pdfDoc = await pdfjsLib.getDocument({ url: props.src }).promise
    pageCount.value = pdfDoc.numPages
    pageNum.value = 1
    loading.value = false
    await nextTick()
    await fitPage()
  } catch (e: unknown) {
    error.value = toUserMessage(e instanceof Error ? e.message : '', 'PDF 加载失败')
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

onMounted(() => {
  loadPdf()
  if (window.ResizeObserver) {
    resizeObserver = new ResizeObserver(() => {
      if (pdfDoc && !loading.value && !error.value) {
        fitPage()
      }
    })
    if (containerRef.value) {
      resizeObserver.observe(containerRef.value)
    }
  }
})

watch(() => props.src, loadPdf)

onBeforeUnmount(() => {
  pdfDoc?.cleanup()
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
})
</script>

<template>
  <div ref="containerRef" class="pdf-preview">
    <div v-if="loading" class="status">加载 PDF…</div>
    <div v-else-if="error" class="status error">{{ error }}</div>
    <template v-else>
      <div class="toolbar">
        <el-button size="small" :disabled="pageNum <= 1" @click="prevPage">上一页</el-button>
        <span class="page-info">{{ pageNum }} / {{ pageCount }}</span>
        <el-button size="small" :disabled="pageNum >= pageCount" @click="nextPage">下一页</el-button>
        <el-divider direction="vertical" />
        <el-button size="small" @click="zoomOut">-</el-button>
        <el-input
          v-model="zoomPercent"
          size="small"
          class="scale-input"
          style="width: 70px;"
          @change="handleZoomPercentChange"
          @keyup.enter="handleZoomPercentChange"
        >
          <template #suffix>%</template>
        </el-input>
        <el-button size="small" @click="zoomIn">+</el-button>
        <el-button size="small" @click="fitPage">合适大小</el-button>
      </div>
      <div class="canvas-viewport">
        <canvas ref="canvasRef" class="canvas" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.pdf-preview {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f1f5f9;
}
.toolbar {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background-color: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  z-index: 10;
  flex-shrink: 0;
}
.page-info, .scale-info {
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  min-width: 48px;
  text-align: center;
}
.scale-input :deep(.el-input__inner) {
  text-align: center;
  padding: 0 4px;
}
.canvas-viewport {
  flex: 1;
  overflow: auto;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 20px;
}
.canvas {
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
  border: 1px solid #cbd5e1;
  background-color: #ffffff;
  max-width: 100%;
}
.status {
  padding: 48px;
  color: #64748b;
  text-align: center;
  background-color: #ffffff;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}
.error {
  color: #ef4444;
}
</style>
