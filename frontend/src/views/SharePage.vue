<script setup lang="ts">

import { ref, onMounted } from 'vue'

import { useRoute } from 'vue-router'

import { ElMessage } from 'element-plus'

import http from '@/api/http'

import PdfPreview from '@/components/PdfPreview.vue'

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

        <div class="cd-share-logo">

          <el-icon :size="28"><Share /></el-icon>

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

          <el-icon :size="20" color="var(--cd-primary)"><Lock /></el-icon>

          <span>请输入提取码</span>

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

                <el-icon :size="36" :color="item.type === 'folder' ? 'var(--cd-file-folder)' : 'var(--cd-file-default)'">

                  <Folder v-if="item.type === 'folder'" />

                  <Document v-else />

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

    </el-dialog>

  </div>

</template>



<style scoped>

.cd-share-page {

  min-height: 100vh;

  padding: 24px;

  background: linear-gradient(135deg, #0F172A 0%, #1E2A4A 40%, #2D1B69 100%);

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

  filter: blur(80px);

  opacity: 0.4;

  animation: orbFloat 8s ease-in-out infinite;

}



.cd-share-orb-1 {

  width: 400px;

  height: 400px;

  background: #4F7CFF;

  top: -100px;

  right: -100px;

}



.cd-share-orb-2 {

  width: 300px;

  height: 300px;

  background: #6366F1;

  bottom: -80px;

  left: -80px;

  animation-delay: -3s;

}



@keyframes orbFloat {

  0%, 100% { transform: translateY(0) scale(1); }

  33% { transform: translateY(-20px) scale(1.05); }

  66% { transform: translateY(10px) scale(0.95); }

}



/* 卡片 */

.cd-share-card {

  width: 100%;

  max-width: 900px;

  padding: 32px;

  position: relative;

  z-index: 1;

  animation: floatUp 0.5s ease;

  margin-top: 40px;

}



/* 头部 */

.cd-share-header {

  display: flex;

  align-items: center;

  gap: 16px;

  padding-bottom: 24px;

  border-bottom: 1px solid rgba(255, 255, 255, 0.1);

  margin-bottom: 24px;

}



.cd-share-logo {

  width: 56px;

  height: 56px;

  border-radius: var(--cd-radius-lg);

  background: var(--cd-primary-gradient);

  display: flex;

  align-items: center;

  justify-content: center;

  color: #fff;

  flex-shrink: 0;

  box-shadow: 0 4px 12px rgba(79, 124, 255, 0.4);

}



.cd-share-title {

  font-size: 22px;

  font-weight: 700;

  color: #fff;

  margin: 0 0 8px;

  word-break: break-all;

}



.cd-share-meta {

  margin: 0;

  display: flex;

  align-items: center;

  gap: 8px;

  color: rgba(255, 255, 255, 0.6);

  font-size: 13px;

}



/* 提取码 */

.cd-extract-area {

  text-align: center;

  padding: 24px 0;

}



.cd-extract-tip {

  display: flex;

  align-items: center;

  justify-content: center;

  gap: 8px;

  color: rgba(255, 255, 255, 0.7);

  font-size: 14px;

  margin-bottom: 16px;

}



.cd-extract-form {

  display: flex;

  gap: 12px;

  max-width: 400px;

  margin: 0 auto;

}



.cd-extract-form :deep(.el-input__wrapper) {

  background: rgba(255, 255, 255, 0.06) !important;

  border: 1px solid rgba(255, 255, 255, 0.12) !important;

  box-shadow: none !important;

}



.cd-extract-form :deep(.el-input__wrapper.is-focus) {

  border-color: var(--cd-primary) !important;

  box-shadow: 0 0 0 3px rgba(79, 124, 255, 0.15) !important;

}



.cd-extract-form :deep(.el-input__inner) {

  color: #fff !important;

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

  margin-bottom: 16px;

}



.cd-share-breadcrumb :deep(.el-breadcrumb__inner),

.cd-share-breadcrumb :deep(.el-breadcrumb__inner a) {

  color: rgba(255, 255, 255, 0.6) !important;

}



.cd-share-breadcrumb :deep(.el-breadcrumb__inner a:hover) {

  color: var(--cd-primary-light) !important;

}



.cd-share-breadcrumb :deep(.el-breadcrumb__separator) {

  color: rgba(255, 255, 255, 0.3) !important;

}



/* 文件网格 */

.cd-share-file-grid {

  display: grid;

  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));

  gap: 12px;

}



.cd-share-file {

  background: rgba(255, 255, 255, 0.06);

  border: 1px solid rgba(255, 255, 255, 0.1);

  border-radius: var(--cd-radius-lg);

  padding: 16px;

  transition: var(--cd-transition);

  cursor: pointer;

}



.cd-share-file:hover {

  background: rgba(255, 255, 255, 0.1);

  border-color: rgba(79, 124, 255, 0.4);

  transform: translateY(-2px);

}



.cd-share-file-icon {

  text-align: center;

  margin-bottom: 12px;

}



.cd-share-file-info {

  text-align: center;

}



.cd-share-file-name {

  color: rgba(255, 255, 255, 0.85);

  font-size: 13px;

  font-weight: 500;

  margin-bottom: 8px;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

}



.cd-share-file-actions {

  display: flex;

  justify-content: center;

  gap: 4px;

}



.cd-share-file-actions :deep(.el-button) {

  color: rgba(255, 255, 255, 0.7) !important;

}



.cd-share-file-actions :deep(.el-button:hover) {

  color: var(--cd-primary-light) !important;

}



/* 单文件 */

.cd-single-actions {

  display: flex;

  justify-content: center;

  gap: 12px;

  padding: 32px 0;

}



.cd-single-actions .el-button:not(.el-button--primary) {

  background: rgba(255, 255, 255, 0.08) !important;

  border: 1px solid rgba(255, 255, 255, 0.15) !important;

  color: rgba(255, 255, 255, 0.85) !important;

}



.cd-single-actions .el-button:not(.el-button--primary):hover {

  background: rgba(255, 255, 255, 0.12) !important;

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

  margin-top: 80px;

  max-width: 400px;

}



.cd-invalid-text {

  color: rgba(255, 255, 255, 0.6);

  font-size: 16px;

  margin: 16px 0 0;

}



/* 预览 */

.cd-share-media {

  max-width: 100%;

  max-height: 75vh;

  display: block;

  margin: 0 auto;

  border-radius: var(--cd-radius);

}

</style>
