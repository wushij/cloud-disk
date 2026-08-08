<script setup lang="ts">

import { ref, onMounted, onUnmounted, watch } from 'vue'

import { useRoute } from 'vue-router'

import { ElMessage } from 'element-plus'

import { shareApi } from '@/api'
import { getApiErrorMessage } from '@/utils/error'

import PdfPreview from '@/components/PdfPreview.vue'
import TextPreview from '@/components/TextPreview.vue'
import { isTextFile } from '@/utils/filePreview'

import VideoPreview from '@/components/VideoPreview.vue'

import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'
import FolderTypeIcon from '@/components/FolderTypeIcon.vue'
import { fileIconColor } from '@/utils/fileMeta'



const route = useRoute()

const code = route.params.code as string



interface ShareItem {

  id: number

  name: string

  type: 'file' | 'folder'

  mimeType?: string

  previewable?: boolean

  officeFile?: boolean

}



interface Breadcrumb {

  id: number

  name: string

}



const info = ref<Record<string, unknown> | null>(null)

const extractCode = ref('')

const verified = ref(false)

const folderItems = ref<ShareItem[]>([])

const breadcrumbs = ref<Breadcrumb[]>([])

const currentFolderId = ref<number | null>(null)

const shareRootFolderId = ref<number | null>(null)

const previewVisible = ref(false)
const videoPreviewRef = ref<InstanceType<typeof VideoPreview> | null>(null)

const isDialogFullscreen = ref(false)

function toggleDialogFullscreen() {
  const el = document.querySelector('.cd-preview-dialog')
  if (!el) return
  if (!document.fullscreenElement) {
    el.requestFullscreen().catch((err) => {
      console.error('全屏失败:', err)
    })
  } else {
    document.exitFullscreen()
  }
}

function handleDialogFullscreenChange() {
  const el = document.querySelector('.cd-preview-dialog')
  isDialogFullscreen.value = document.fullscreenElement === el
}

function onPreviewClosed() {
  videoPreviewRef.value?.stop?.()
  if (document.fullscreenElement) {
    document.exitFullscreen()
  }
}

watch(previewVisible, (visible) => {
  if (!visible) {
    onPreviewClosed()
  }
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleDialogFullscreenChange)
})

const previewName = ref('')

const previewUrl = ref('')

const previewMime = ref('')

const onlyOfficeConfig = ref<{ documentServerUrl: string; config: Record<string, unknown> } | null>(null)



onMounted(async () => {

  document.addEventListener('fullscreenchange', handleDialogFullscreenChange)

  try {

    const data = (await shareApi.publicDetail(code)) as any

    info.value = data

    if (!data.needExtractCode) {

      verified.value = true

      if (data.shareType === 'FOLDER') {

        shareRootFolderId.value = data.folderId ?? null

        await loadFolderItems()

      }

    }

  } catch {

    /* global toast */

  }

})



async function loadFolderItems(folderId?: number) {

  const data = (await shareApi.publicFolderItems(code, folderId)) as any

  folderItems.value = (data.items || []) as any

  breadcrumbs.value = data.breadcrumbs || []

  currentFolderId.value = data.currentFolderId ?? shareRootFolderId.value

  if (shareRootFolderId.value == null && data.shareRootFolderId != null) {

    shareRootFolderId.value = data.shareRootFolderId

  }

}



async function verify() {

  if (!extractCode.value.trim()) {

    ElMessage.warning('请输入提取码')

    return

  }

  try {

    await shareApi.publicAccess(code, extractCode.value.trim())

    verified.value = true

    ElMessage.success('验证成功')

    if (info.value?.shareType === 'FOLDER') {

      shareRootFolderId.value = (info.value.folderId as number) ?? null

      await loadFolderItems()

    }

  } catch (e) {

    ElMessage.error(getApiErrorMessage(e, '提取码错误，请重新输入'))

  }

}



function q(extra = '') {

  if (!extra) return ''

  return `?${extra}`

}



function downloadFile(fileId?: number) {

  window.open(`/share/${code}/download${q(fileId ? `fileId=${fileId}` : '')}`, '_blank')

}



async function openFolder(row: ShareItem) {

  await loadFolderItems(row.id)

}



async function gotoCrumb(crumb: Breadcrumb) {

  await loadFolderItems(crumb.id)

}



async function resolveSharePreviewUrl(fileId: number): Promise<string> {

  try {

    const data = await shareApi.publicDirectUrl(code, fileId)

    if (data.url) return data.url

  } catch {

    /* fallback proxy preview */

  }

  return `/share/${code}/preview${q(`fileId=${fileId}`)}`

}



async function previewItem(row: ShareItem) {

  previewName.value = row.name

  previewMime.value = row.mimeType || ''

  onlyOfficeConfig.value = null

  previewUrl.value = ''

  if (row.officeFile) {

    const data = await shareApi.publicOnlyOffice(code, row.id)

    onlyOfficeConfig.value = { documentServerUrl: data.documentServerUrl as string, config: data.config as Record<string, unknown> }

  } else {

    previewUrl.value = await resolveSharePreviewUrl(row.id)

  }

  previewVisible.value = true

}



async function previewSingle() {

  const fileId = info.value?.fileId as number

  await previewItem({

    id: fileId,

    name: String(info.value?.fileName || ''),

    type: 'file',

    mimeType: String(info.value?.mimeType || ''),

    previewable: !!info.value?.previewable,

    officeFile: !!info.value?.officeFile

  })

}

// ── 列表文件辅助 ──────────────────────────────────────────────────
const isArchive = (item: ShareItem) => {
  if (item.type !== 'file') return false
  const name = item.name.toLowerCase()
  const ext = name.split('.').pop() || ''
  return ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz'].includes(ext)
}

const isImageItem = (item: ShareItem) => {
  if (item.type !== 'file') return false
  const mime = (item.mimeType || '').toLowerCase()
  if (mime.startsWith('image/')) return true
  const ext = (item.name.split('.').pop() || '').toLowerCase()
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(ext)
}

/** 取文件后缀（大写） */
function itemExt(item: ShareItem): string {
  if (item.type === 'folder') return ''
  const dot = item.name.lastIndexOf('.')
  return dot > 0 ? item.name.substring(dot + 1).toUpperCase() : ''
}

/** 复用 fileIconColor，将 ShareItem 映射为简单对象 */
function itemIconColor(item: ShareItem): string {
  return fileIconColor({ type: item.type, mimeType: item.mimeType } as any)
}

function getFileIconComponent(item: ShareItem): string {
  if (item.type === 'folder') return 'Folder'
  const mime = (item.mimeType || '').toLowerCase()
  const ext = (item.name.split('.').pop() || '').toLowerCase()
  if (mime.startsWith('image/') || ['jpg','jpeg','png','gif','webp','svg'].includes(ext)) return 'Picture'
  if (mime.startsWith('video/') || ['mp4','mkv','avi','mov','flv'].includes(ext)) return 'VideoPlay'
  if (mime.startsWith('audio/') || ['mp3','wav','ogg','flac'].includes(ext)) return 'Headset'
  return 'Document'
}

// ── 单文件分享头部辅助 ─────────────────────────────────────────────
function isSingleImageShare() {
  if (!info.value) return false
  if (info.value.shareType === 'FOLDER') return false
  const mime = String(info.value.mimeType || '').toLowerCase()
  const ext = String(info.value.fileName || '').toLowerCase().split('.').pop() || ''
  return mime.startsWith('image/') || ['jpg','jpeg','png','gif','webp','svg'].includes(ext)
}

function isSingleArchiveShare() {
  if (!info.value) return false
  if (info.value.shareType === 'FOLDER') return false
  const ext = String(info.value.fileName || '').toLowerCase().split('.').pop() || ''
  return ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz'].includes(ext)
}

function getSingleFileIconComponent(): string {
  if (!info.value) return 'Document'
  const mime = String(info.value.mimeType || '').toLowerCase()
  const ext = String(info.value.fileName || '').toLowerCase().split('.').pop() || ''
  if (mime.startsWith('image/') || ['jpg','jpeg','png','gif','webp','svg'].includes(ext)) return 'Picture'
  if (mime.startsWith('video/') || ['mp4','mkv','avi','mov','flv'].includes(ext)) return 'VideoPlay'
  if (mime.startsWith('audio/') || ['mp3','wav','ogg','flac'].includes(ext)) return 'Headset'
  return 'Document'
}

function getSingleFileIconColor(): string {
  if (!info.value) return 'var(--cd-file-default)'
  return fileIconColor({ type: 'file', mimeType: String(info.value.mimeType || '') } as any)
}

function getSingleFileExt(): string {
  if (!info.value) return ''
  const name = String(info.value.fileName || '')
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.substring(dot + 1).toUpperCase() : ''
}

function getSingleShareImageUrl() {
  if (!info.value || !info.value.fileId) return ''
  return `/share/${code}/preview${q(`fileId=${info.value.fileId}`)}`
}

</script>



<template>

  <div class="cd-share-page">

    <!-- 背景装饰 -->

    <div class="cd-share-bg">

      <div class="cd-share-orb cd-share-orb-1" />

      <div class="cd-share-orb cd-share-orb-2" />

    </div>



    <!-- 分享卡片 -->

    <div v-if="info" class="cd-share-card cd-glass">

      <!-- 头部 -->
      <div class="cd-share-header">
        <div
          class="cd-share-logo"
          :class="{
            'cd-share-logo-img': isSingleImageShare() && (verified || !info.needExtractCode),
            'cd-share-logo-folder': info.shareType === 'FOLDER' || isSingleArchiveShare(),
            'cd-share-logo-file': info.shareType !== 'FOLDER' && !isSingleImageShare() && !isSingleArchiveShare()
          }"
        >
          <!-- 图片：显示缩略图 -->
          <img
            v-if="isSingleImageShare() && (verified || !info.needExtractCode)"
            :src="getSingleShareImageUrl()"
            class="cd-share-logo-cover"
            alt=""
          />
          <!-- 文件夹分享 -->
          <FolderTypeIcon v-else-if="info.shareType === 'FOLDER'" :size="56" />
          <!-- 压缩包：带拉链的 FolderTypeIcon -->
          <FolderTypeIcon v-else-if="isSingleArchiveShare()" :archive="true" :size="56" />
          <!-- 图片（未验证/无需验证）：通用图片图标 -->
          <div
            v-else-if="isSingleImageShare()"
            class="cd-share-logo-icon"
            :style="{ color: getSingleFileIconColor() }"
          >
            <el-icon :size="32"><component :is="getSingleFileIconComponent()" /></el-icon>
            <span v-if="getSingleFileExt()" class="cd-share-logo-ext">{{ getSingleFileExt() }}</span>
          </div>
          <!-- 其他文件类型 -->
          <div
            v-else
            class="cd-share-logo-icon"
            :style="{ color: getSingleFileIconColor() }"
          >
            <el-icon :size="32"><component :is="getSingleFileIconComponent()" /></el-icon>
            <span v-if="getSingleFileExt()" class="cd-share-logo-ext">{{ getSingleFileExt() }}</span>
          </div>
        </div>
        <div class="cd-share-title-area">
          <h1 class="cd-share-title">{{ info.shareType === 'FOLDER' ? info.folderName : info.fileName }}</h1>
          <p class="cd-share-meta">
            <el-tag v-if="info.shareType === 'FOLDER'" type="warning" size="small" round>
              <el-icon><FolderOpened /></el-icon> 文件夹分享
            </el-tag>
            <span v-else-if="info.fileSize">{{ (((info.fileSize as number) || 0) / 1024 / 1024).toFixed(2) }} MB</span>
          </p>
        </div>
      </div>

      <!-- 提取码 -->
      <div v-if="info.needExtractCode && !verified" class="cd-extract-area">
        <div class="cd-extract-tip">
          <el-icon :size="20" color="#3b82f6"><Lock /></el-icon>
          <span>此分享需要提取码</span>
        </div>

        <div class="cd-extract-form">

          <el-input

            v-model="extractCode"

            placeholder="请输入提取码"

            size="large"

            @keyup.enter="verify"

          />

          <el-button type="primary" size="large" round @click="verify">

            <el-icon><Check /></el-icon>

            验证

          </el-button>

        </div>

      </div>



      <!-- 已验证内容 -->

      <div v-if="verified || !info.needExtractCode" class="cd-share-content">

        <!-- 文件夹视图 -->

        <template v-if="info.shareType === 'FOLDER'">

          <el-breadcrumb v-if="breadcrumbs.length" separator="/" class="cd-share-breadcrumb">

            <el-breadcrumb-item v-for="crumb in breadcrumbs" :key="crumb.id">

              <a href="#" @click.prevent="gotoCrumb(crumb)">{{ crumb.name }}</a>

            </el-breadcrumb-item>

          </el-breadcrumb>



          <el-empty v-if="!folderItems.length" description="文件夹是空的" />



          <div v-else class="cd-share-file-grid">

            <div

              v-for="item in folderItems"

              :key="item.id"

              class="cd-share-file"

              @dblclick="item.type === 'folder' ? openFolder(item) : null"

            >

              <div
                class="cd-share-file-icon"
                :class="{ 'cd-share-file-icon-cover': isImageItem(item) }"
                :style="isImageItem(item) ? {} : { color: itemIconColor(item) }"
              >
                <!-- 文件夹 -->
                <FolderTypeIcon v-if="item.type === 'folder'" :size="48" />
                <!-- 压缩包 -->
                <FolderTypeIcon v-else-if="isArchive(item)" :archive="true" :size="48" />
                <!-- 图片：显示真实缩略图 -->
                <img
                  v-else-if="isImageItem(item)"
                  :src="`/share/${code}/preview?fileId=${item.id}`"
                  class="cd-share-file-thumb"
                  alt=""
                />
                <!-- 其他文件：图标 + ext 标签 -->
                <template v-else>
                  <el-icon :size="32"><component :is="getFileIconComponent(item)" /></el-icon>
                  <span v-if="itemExt(item)" class="cd-share-file-ext">{{ itemExt(item) }}</span>
                </template>
              </div>


              <div class="cd-share-file-info">

                <div class="cd-share-file-name" :title="item.name">{{ item.name }}</div>

                <div class="cd-share-file-actions">

                  <el-button v-if="item.type === 'folder'" link type="primary" size="small" @click="openFolder(item)">

                    <el-icon><FolderOpened /></el-icon>打开

                  </el-button>

                  <template v-else>

                    <el-button link type="primary" size="small" @click="downloadFile(item.id)">

                      <el-icon><Download /></el-icon>下载

                    </el-button>

                    <el-button v-if="item.previewable" link size="small" @click="previewItem(item)">

                      <el-icon><View /></el-icon>预览

                    </el-button>

                  </template>

                </div>

              </div>

            </div>

          </div>

        </template>



        <!-- 单文件视图 -->

        <template v-else>

          <div class="cd-single-actions">

            <el-button type="primary" size="large" @click="downloadFile(info.fileId as number)">

              <el-icon><Download /></el-icon>

              下载文件

            </el-button>

            <el-button v-if="info.previewable" size="large" @click="previewSingle">

              <el-icon><View /></el-icon>

              在线预览

            </el-button>

          </div>

        </template>

      </div>

    </div>



    <!-- 失效 -->

    <div v-else class="cd-share-invalid cd-glass">

      <el-icon :size="56" color="var(--cd-text-placeholder)"><WarningFilled /></el-icon>

      <p class="cd-invalid-text">分享不存在或已过期</p>

    </div>



    <!-- 预览弹窗 -->

    <el-dialog
      v-model="previewVisible"
      :title="previewName"
      width="90%"
      destroy-on-close
      top="4vh"
      class="cd-preview-dialog"
      @closed="onPreviewClosed"
    >

      <!-- 弹窗全屏按钮 -->
      <button
        v-if="previewVisible && !previewMime.startsWith('video/')"
        class="cd-dialog-fullscreen-btn"
        :title="isDialogFullscreen ? '退出全屏 (Esc)' : '全屏'"
        @click="toggleDialogFullscreen"
      >
        <svg v-if="!isDialogFullscreen" viewBox="0 0 24 24" class="cd-fullscreen-icon">
          <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3" />
        </svg>
        <svg v-else viewBox="0 0 24 24" class="cd-fullscreen-icon">
          <path d="M4 14h6v6m10-6h-6v6M4 10h6V4m10 6h-6V4" />
        </svg>
      </button>

      <OnlyOfficeEditor v-if="onlyOfficeConfig" :document-server-url="onlyOfficeConfig.documentServerUrl" :config="onlyOfficeConfig.config" />

      <div v-else-if="previewMime.startsWith('image/')" class="cd-preview-image-wrap">
        <img :src="previewUrl" class="cd-share-media" alt="" />
      </div>

      <VideoPreview
        v-else-if="previewMime.startsWith('video/')"
        ref="videoPreviewRef"
        :key="previewUrl"
        :src="previewUrl"
      />

      <PdfPreview v-else-if="previewMime.includes('pdf')" :src="previewUrl" />

      <TextPreview v-else-if="isTextFile(previewMime, previewName)" :src="previewUrl" />

      <el-empty v-else description="暂不支持该类型预览" />

    </el-dialog>

  </div>

</template>



<style scoped>

.cd-share-page {

  min-height: 100vh;

  padding: 24px;

  background: linear-gradient(135deg, #f0f4ff 0%, #f8f9fd 50%, #fdf2f8 100%);

  display: flex;

  justify-content: center;

  align-items: flex-start;

  position: relative;

  overflow: hidden;

}



/* 背景装饰 */

.cd-share-bg {

  position: absolute;

  inset: 0;

  overflow: hidden;

  pointer-events: none;

}



.cd-share-orb {

  position: absolute;

  border-radius: 50%;

  filter: blur(120px);

  opacity: 0.18;

  animation: orbFloat 10s ease-in-out infinite;

}



.cd-share-orb-1 {

  width: 500px;

  height: 500px;

  background: #93c5fd;

  top: -120px;

  right: -120px;

}



.cd-share-orb-2 {

  width: 400px;

  height: 400px;

  background: #c4b5fd;

  bottom: -100px;

  left: -100px;

  animation-delay: -4s;

}



@keyframes orbFloat {

  0%, 100% { transform: translateY(0) scale(1); }

  33% { transform: translateY(-30px) scale(1.08); }

  66% { transform: translateY(15px) scale(0.92); }

}



/* 卡片 */

.cd-share-card {

  width: 100%;

  max-width: 860px;

  padding: 40px;

  position: relative;

  z-index: 1;

  background: rgba(255, 255, 255, 0.72);

  backdrop-filter: blur(24px);

  -webkit-backdrop-filter: blur(24px);

  border-radius: var(--cd-radius-xl);

  border: 1px solid rgba(240, 212, 212, 0.72);

  box-shadow: 0 6px 28px rgba(239, 68, 68, 0.08), 0 2px 10px rgba(239, 68, 68, 0.05);

  margin-top: 60px;

  animation: floatUp 0.6s cubic-bezier(0.16, 1, 0.3, 1);

}



@keyframes floatUp {

  from { opacity: 0; transform: translateY(40px); }

  to { opacity: 1; transform: translateY(0); }

}



/* 头部 */

.cd-share-header {

  display: flex;

  align-items: center;

  gap: 20px;

  padding-bottom: 28px;

  border-bottom: 1px solid var(--cd-border-light);

  margin-bottom: 28px;

}



.cd-share-logo {

  width: 68px;

  height: 68px;

  border-radius: 20px;

  background: var(--cd-primary-bg, rgba(99, 102, 241, 0.08));

  display: flex;

  align-items: center;

  justify-content: center;

  color: var(--cd-primary, #6366f1);

  flex-shrink: 0;

  box-shadow: none;

}

.cd-share-logo-svg {
  width: 32px;
  height: 32px;
  fill: currentColor;
}

/* 文件夹/压缩包：去掉背景，让彩色 SVG 图标自然呈现 */
.cd-share-logo-folder {
  background: transparent;
  box-shadow: none;
}

/* 普通文件类型：去掉背景，与文件夹/压缩包保持一致 */
.cd-share-logo-file {
  background: transparent;
  box-shadow: none;
}

/* 头部图标容器（图标 + ext 标签纵向排列） */
.cd-share-logo-icon {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
}

/* 头部 ext 标签（PDF / DOCX / MP4 等） */
.cd-share-logo-ext {
  font-size: 10px;
  font-weight: 700;
  color: currentColor;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(4px);
  padding: 1px 7px;
  border-radius: 999px;
  letter-spacing: 0.5px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}

/* 文件列表中的 ext 标签 */
.cd-share-file-ext {
  font-size: 10px;
  font-weight: 700;
  color: currentColor;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(4px);
  padding: 2px 8px;
  border-radius: 999px;
  letter-spacing: 0.5px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  margin-top: 4px;
  display: block;
}



.cd-share-title {

  font-size: 24px;

  font-weight: 800;

  color: var(--cd-text-primary, #0f172a);

  margin: 0 0 6px;

  letter-spacing: -0.5px;

  word-break: break-all;

}



.cd-share-meta {

  margin: 0;

  display: flex;

  align-items: center;

  gap: 10px;

  color: var(--cd-text-secondary, #64748b);

  font-size: 13px;

  font-weight: 500;

}



/* 提取码 */

.cd-extract-area {

  text-align: center;

  padding: 36px 0;

}



.cd-extract-tip {

  display: flex;

  align-items: center;

  justify-content: center;

  width: 100%;

  gap: 10px;

  color: var(--cd-text-secondary, #475569);

  font-size: 15px;

  font-weight: 600;

  margin-bottom: 24px;

}



.cd-extract-form {

  display: flex;

  gap: 16px;

  max-width: 440px;

  margin: 0 auto;

}



.cd-extract-form :deep(.el-input__wrapper) {

  background: #fff !important;

  border: 1px solid var(--cd-border, #e2e8f0) !important;

  box-shadow: none !important;

  border-radius: var(--cd-radius-lg) !important;

  transition: all var(--cd-transition-fast) !important;

}



.cd-extract-form :deep(.el-input__wrapper.is-focus) {

  border-color: rgba(59, 130, 246, 0.6) !important;

  background: #fff !important;

  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.1) !important;

}



.cd-extract-form :deep(.el-input__inner) {

  color: var(--cd-text-primary, #0f172a) !important;

  font-weight: 500;

}



.cd-extract-form :deep(.el-input__inner::placeholder) {

  color: var(--cd-text-placeholder, #94a3b8) !important;

}



.cd-extract-form .el-button {

  flex-shrink: 0;

}



/* 内容 */

.cd-share-content {

  min-height: 200px;

}



.cd-share-breadcrumb {

  margin-bottom: 20px;

}



.cd-share-breadcrumb :deep(.el-breadcrumb__inner),

.cd-share-breadcrumb :deep(.el-breadcrumb__inner a) {

  color: var(--cd-text-secondary, #64748b) !important;

  font-weight: 600;

}



.cd-share-breadcrumb :deep(.el-breadcrumb__inner a:hover) {

  color: #3b82f6 !important;

}



.cd-share-breadcrumb :deep(.el-breadcrumb__separator) {

  color: var(--cd-text-placeholder, #94a3b8) !important;

}



/* 文件网格 */

.cd-share-file-grid {

  display: grid;

  grid-template-columns: repeat(auto-fill, minmax(185px, 1fr));

  gap: 16px;

}



.cd-share-file {

  background: #fff;

  border: 1px solid var(--cd-border-light, #f1f5f9);

  border-radius: var(--cd-radius-lg);

  overflow: hidden;

  transition: all var(--cd-transition);

  cursor: pointer;

  box-shadow: var(--cd-shadow-card, 0 2px 8px rgba(0,0,0,0.06));

  display: flex;

  flex-direction: column;

}



.cd-share-file:hover {

  background: var(--cd-bg-surface, #f8f9fd);

  border-color: rgba(59, 130, 246, 0.4);

  transform: translateY(-4px);

  box-shadow: 0 12px 30px rgba(59, 130, 246, 0.12);

}



.cd-share-file-icon {

  display: flex;

  flex-direction: column;

  align-items: center;

  justify-content: center;

  gap: 6px;

  height: 110px;

  margin-bottom: 0;

  padding: 0 12px;

}

/* 图片类：铺满作为封面 */
.cd-share-file-icon-cover {
  margin-bottom: 0;
  width: 100%;
  height: 110px;
  overflow: hidden;
  border-radius: var(--cd-radius-lg) var(--cd-radius-lg) 0 0;
  gap: 0;
}

.cd-share-file-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.25s ease;
}

.cd-share-file:hover .cd-share-file-thumb {
  transform: scale(1.05);
}



.cd-share-file-info {

  text-align: center;

  padding: 14px 14px 14px;

  flex: 1;

  display: flex;

  flex-direction: column;

  justify-content: space-between;

}



.cd-share-file-name {

  color: var(--cd-text-primary, #0f172a);

  font-size: 14px;

  font-weight: 600;

  margin-bottom: 12px;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

}



.cd-share-file-actions {

  display: flex;

  justify-content: center;

  gap: 8px;

}



.cd-share-file-actions :deep(.el-button) {

  color: var(--cd-text-secondary, #64748b) !important;

  font-weight: 700;

  border: none !important;

  box-shadow: none !important;

  background: transparent !important;

}



.cd-share-file-actions :deep(.el-button:hover) {

  color: #3b82f6 !important;

  background: transparent !important;

}



/* 单文件 */

.cd-single-actions {

  display: flex;

  justify-content: center;

  gap: 16px;

  padding: 40px 0 20px;

}



.cd-single-actions .el-button:not(.el-button--primary) {

  background: #fff !important;

  border: 1px solid var(--cd-border, #e2e8f0) !important;

  color: var(--cd-text-primary, #0f172a) !important;

}



.cd-single-actions .el-button:not(.el-button--primary):hover {

  background: var(--cd-bg-surface, #f8f9fd) !important;

  color: var(--cd-primary, #3b82f6) !important;

}



/* 失效状态 */

.cd-share-invalid {

  display: flex;

  flex-direction: column;

  align-items: center;

  justify-content: center;

  padding: 80px 40px;

  text-align: center;

  margin: 80px auto 0;

  max-width: 440px;

  background: rgba(255, 255, 255, 0.72);

  border-radius: var(--cd-radius-xl);

  border: 1px solid rgba(240, 212, 212, 0.72);

  box-shadow: 0 6px 28px rgba(239, 68, 68, 0.08);

}



.cd-invalid-text {

  color: var(--cd-text-secondary, #64748b);

  font-size: 16px;

  font-weight: 600;

  margin: 18px 0 0;

}



/* 预览 */
.cd-share-media {
  max-width: 100%;
  max-height: 75vh;
  display: block;
  margin: 0 auto;
  border-radius: var(--cd-radius);
}

.cd-share-logo-img {
  background: transparent !important;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1) !important;
  overflow: hidden;
}

.cd-share-logo-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 20px;
}
</style>
