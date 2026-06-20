<script setup lang="ts">

import { ref, onMounted } from 'vue'

import { useRoute } from 'vue-router'

import { ElMessage } from 'element-plus'

import http from '@/api/http'

import PdfPreview from '@/components/PdfPreview.vue'
import TextPreview from '@/components/TextPreview.vue'
import { isTextFile } from '@/utils/filePreview'

import VideoPreview from '@/components/VideoPreview.vue'

import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'



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

const previewName = ref('')

const previewUrl = ref('')

const previewMime = ref('')

const onlyOfficeConfig = ref<{ documentServerUrl: string; config: Record<string, unknown> } | null>(null)



onMounted(async () => {

  try {

    const { data } = await http.get(`/share/${code}`)

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

  const { data } = await http.get(`/share/${code}/items`, {

    params: { extractCode: extractCode.value || undefined, folderId: folderId ?? undefined }

  })

  folderItems.value = data.items || []

  breadcrumbs.value = data.breadcrumbs || []

  currentFolderId.value = data.currentFolderId ?? shareRootFolderId.value

  if (shareRootFolderId.value == null && data.shareRootFolderId != null) {

    shareRootFolderId.value = data.shareRootFolderId

  }

}



async function verify() {

  try {

    await http.post(`/share/${code}/access`, { extractCode: extractCode.value })

    verified.value = true

    ElMessage.success('验证成功')

    if (info.value?.shareType === 'FOLDER') {

      shareRootFolderId.value = (info.value.folderId as number) ?? null

      await loadFolderItems()

    }

  } catch {

    /* global toast */

  }

}



function q(extra = '') {

  const base = extractCode.value ? `?extractCode=${encodeURIComponent(extractCode.value)}` : ''

  if (!extra) return base

  return base ? `${base}&${extra}` : `?${extra}`

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

    const { data } = await http.get(`/share/${code}/direct-url`, {

      params: { fileId, extractCode: extractCode.value || undefined }

    })

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

    const { data } = await http.get(`/share/${code}/onlyoffice/${row.id}${q()}`)

    onlyOfficeConfig.value = { documentServerUrl: data.documentServerUrl, config: data.config }

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

function getFileIconName(item: ShareItem) {
  if (item.type === 'folder') return 'Folder'
  const name = item.name.toLowerCase()
  const ext = name.split('.').pop() || ''
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) return 'Picture'
  if (['mp4', 'mkv', 'avi', 'mov', 'flv'].includes(ext)) return 'VideoPlay'
  if (['mp3', 'wav', 'ogg', 'flac'].includes(ext)) return 'Headset'
  if (['pdf', 'doc', 'docx', 'txt', 'md'].includes(ext)) return 'Notebook'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'Files'
  return 'Document'
}

function getIconColorStyle(item: ShareItem) {
  if (item.type === 'folder') return '#f59e0b'
  const name = item.name.toLowerCase()
  const ext = name.split('.').pop() || ''
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) return '#10b981'
  if (['mp4', 'mkv', 'avi', 'mov', 'flv'].includes(ext)) return '#8b5cf6'
  if (['mp3', 'wav', 'ogg', 'flac'].includes(ext)) return '#ec4899'
  if (['pdf', 'doc', 'docx', 'txt', 'md'].includes(ext)) return '#3b82f6'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '#f97316'
  return '#94a3b8'
}

function isSingleImageShare() {
  if (!info.value) return false
  if (info.value.shareType === 'FOLDER') return false
  const name = String(info.value.fileName || '').toLowerCase()
  const ext = name.split('.').pop() || ''
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)
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
        <div class="cd-share-logo" :class="{ 'cd-share-logo-img': isSingleImageShare() && (verified || !info.needExtractCode) }">
          <img
            v-if="isSingleImageShare() && (verified || !info.needExtractCode)"
            :src="getSingleShareImageUrl()"
            class="cd-share-logo-cover"
            alt=""
          />
          <el-icon v-else :size="28"><Share /></el-icon>
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

          <el-button type="primary" size="large" @click="verify">

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

              <div class="cd-share-file-icon">

                <el-icon :size="36" :style="{ color: getIconColorStyle(item) }">

                  <component :is="getFileIconName(item)" />

                </el-icon>

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

    <el-dialog v-model="previewVisible" :title="previewName" width="90%" destroy-on-close top="4vh">

      <OnlyOfficeEditor v-if="onlyOfficeConfig" :document-server-url="onlyOfficeConfig.documentServerUrl" :config="onlyOfficeConfig.config" />

      <img v-else-if="previewMime.startsWith('image/')" :src="previewUrl" class="cd-share-media" alt="" />

      <VideoPreview v-else-if="previewMime.startsWith('video/')" :src="previewUrl" />

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

  background: linear-gradient(135deg, #070b19 0%, #0d1527 50%, #171026 100%);

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

  opacity: 0.28;

  animation: orbFloat 10s ease-in-out infinite;

}



.cd-share-orb-1 {

  width: 500px;

  height: 500px;

  background: #3b82f6;

  top: -120px;

  right: -120px;

}



.cd-share-orb-2 {

  width: 400px;

  height: 400px;

  background: #7c3aed;

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

  background: rgba(13, 21, 39, 0.55);

  backdrop-filter: blur(24px);

  -webkit-backdrop-filter: blur(24px);

  border-radius: var(--cd-radius-xl);

  border: 1px solid rgba(255, 255, 255, 0.08);

  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.08);

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

  border-bottom: 1px solid rgba(255, 255, 255, 0.08);

  margin-bottom: 28px;

}



.cd-share-logo {

  width: 68px;

  height: 68px;

  border-radius: 20px;

  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);

  display: flex;

  align-items: center;

  justify-content: center;

  color: #fff;

  flex-shrink: 0;

  box-shadow: 0 8px 24px rgba(59, 130, 246, 0.3);

}



.cd-share-title {

  font-size: 24px;

  font-weight: 800;

  color: #fff;

  margin: 0 0 6px;

  letter-spacing: -0.5px;

  word-break: break-all;

}



.cd-share-meta {

  margin: 0;

  display: flex;

  align-items: center;

  gap: 10px;

  color: rgba(255, 255, 255, 0.5);

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

  gap: 10px;

  color: rgba(255, 255, 255, 0.7);

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

  background: rgba(255, 255, 255, 0.04) !important;

  border: 1px solid rgba(255, 255, 255, 0.1) !important;

  box-shadow: none !important;

  border-radius: var(--cd-radius-lg) !important;

  transition: all var(--cd-transition-fast) !important;

}



.cd-extract-form :deep(.el-input__wrapper.is-focus) {

  border-color: rgba(59, 130, 246, 0.6) !important;

  background: rgba(255, 255, 255, 0.08) !important;

  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.15) !important;

}



.cd-extract-form :deep(.el-input__inner) {

  color: #fff !important;

  font-weight: 500;

}



.cd-extract-form :deep(.el-input__inner::placeholder) {

  color: rgba(255, 255, 255, 0.35) !important;

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

  color: rgba(255, 255, 255, 0.6) !important;

  font-weight: 600;

}



.cd-share-breadcrumb :deep(.el-breadcrumb__inner a:hover) {

  color: #3b82f6 !important;

}



.cd-share-breadcrumb :deep(.el-breadcrumb__separator) {

  color: rgba(255, 255, 255, 0.3) !important;

}



/* 文件网格 */

.cd-share-file-grid {

  display: grid;

  grid-template-columns: repeat(auto-fill, minmax(185px, 1fr));

  gap: 16px;

}



.cd-share-file {

  background: rgba(255, 255, 255, 0.03);

  border: 1px solid rgba(255, 255, 255, 0.06);

  border-radius: var(--cd-radius-lg);

  padding: 20px;

  transition: all var(--cd-transition);

  cursor: pointer;

  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);

}



.cd-share-file:hover {

  background: rgba(255, 255, 255, 0.08);

  border-color: rgba(59, 130, 246, 0.4);

  transform: translateY(-4px);

  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.25);

}



.cd-share-file-icon {

  text-align: center;

  margin-bottom: 16px;

}



.cd-share-file-info {

  text-align: center;

}



.cd-share-file-name {

  color: rgba(255, 255, 255, 0.9);

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

  color: rgba(255, 255, 255, 0.55) !important;

  font-weight: 700;

}



.cd-share-file-actions :deep(.el-button:hover) {

  color: #3b82f6 !important;

}



/* 单文件 */

.cd-single-actions {

  display: flex;

  justify-content: center;

  gap: 16px;

  padding: 40px 0 20px;

}



.cd-single-actions .el-button:not(.el-button--primary) {

  background: rgba(255, 255, 255, 0.05) !important;

  border: 1px solid rgba(255, 255, 255, 0.1) !important;

  color: rgba(255, 255, 255, 0.85) !important;

}



.cd-single-actions .el-button:not(.el-button--primary):hover {

  background: rgba(255, 255, 255, 0.1) !important;

  color: #fff !important;

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

  background: rgba(13, 21, 39, 0.55);

  border-radius: var(--cd-radius-xl);

  border: 1px solid rgba(255, 255, 255, 0.08);

  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.4);

}



.cd-invalid-text {

  color: rgba(255, 255, 255, 0.6);

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
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25) !important;
  overflow: hidden;
}

.cd-share-logo-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 20px;
}
</style>
