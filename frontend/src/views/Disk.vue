<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import http, { TOKEN_KEY } from '@/api/http'
import { fmtSize } from '@/utils/md5'
import { useFileStore, type FileItem } from '@/stores/file'
import { useUploadStore, promptCreateFolder } from '@/stores/upload'
import ShareDialog from '@/components/ShareDialog.vue'
import MoveCopyDialog from '@/components/MoveCopyDialog.vue'
import FolderTree from '@/components/FolderTree.vue'
import FileGridView from '@/components/FileGridView.vue'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'
import PdfPreview from '@/components/PdfPreview.vue'
import VideoPreview from '@/components/VideoPreview.vue'
import { connectUploadWs, disconnectUploadWs } from '@/utils/ws'

const fileStore = useFileStore()
const uploadStore = useUploadStore()
const { currentFolderId, breadcrumb, items, loading, keyword, fileType } = storeToRefs(fileStore)
const { tasks: uploadTasks } = storeToRefs(uploadStore)

const viewMode = ref<'list' | 'grid'>('grid')
const shareVisible = ref(false)
const shareFileId = ref<number | null>(null)
const shareFolderId = ref<number | null>(null)
const shareItemName = ref('')
const moveCopyVisible = ref(false)
const moveCopyMode = ref<'move' | 'copy'>('move')
const moveCopyItem = ref<FileItem | null>(null)
const previewVisible = ref(false)
const previewUrl = ref('')
const previewType = ref('')
const previewName = ref('')
const onlyOfficeConfig = ref<{ documentServerUrl: string; config: Record<string, unknown> } | null>(null)
const folderTreeRef = ref<InstanceType<typeof FolderTree> | null>(null)
const treeCollapsed = ref(false)

const fileInput = ref<HTMLInputElement | null>(null)
const folderInput = ref<HTMLInputElement | null>(null)

function transcodeLabel(status?: string) {
  switch (status) {
    case 'PENDING':
    case 'PROCESSING':
      return '转码中'
    case 'DONE':
      return '已转码'
    case 'FAILED':
      return '转码失败'
    default:
      return ''
  }
}

function tokenParam() {
  return encodeURIComponent(localStorage.getItem(TOKEN_KEY) || '')
}

async function refreshAfterChange() {
  folderTreeRef.value?.reload()
  await fileStore.loadList()
}

async function createFolder() {
  const ok = await promptCreateFolder(currentFolderId.value)
  if (ok) await refreshAfterChange()
}

async function processFiles(files: File[]) {
  await uploadStore.processFiles(files, currentFolderId.value, refreshAfterChange)
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.length) void processFiles(Array.from(input.files))
  input.value = ''
}

function onFolderChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.length) void processFiles(Array.from(input.files))
  input.value = ''
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  if (e.dataTransfer?.files?.length) void processFiles(Array.from(e.dataTransfer.files))
}

function download(row: FileItem) {
  if (row.type !== 'file') return
  const a = document.createElement('a')
  a.href = `/api/files/${row.id}/download?access_token=${tokenParam()}`
  a.download = row.name
  a.click()
}

async function directDownload(row: FileItem) {
  try {
    const { data } = await http.get(`/api/files/${row.id}/direct-url`)
    window.open(data.url, '_blank')
  } catch {
    download(row)
  }
}

async function resolvePreviewUrl(fileId: number): Promise<string> {
  try {
    const { data } = await http.get(`/api/files/${fileId}/direct-url`)
    if (data.url) return data.url
  } catch {
    /* fallback proxy preview */
  }
  return `/api/files/${fileId}/preview?access_token=${tokenParam()}`
}

async function preview(row: FileItem) {
  if (!row.previewable) {
    ElMessage.info('该文件类型暂不支持预览')
    return
  }
  previewName.value = row.name
  previewType.value = row.mimeType || ''
  onlyOfficeConfig.value = null
  if (row.officeFile) {
    try {
      const { data } = await http.get(`/api/files/${row.id}/onlyoffice`)
      if (data.documentServerUrl && data.config) {
        onlyOfficeConfig.value = {
          documentServerUrl: data.documentServerUrl,
          config: data.config
        }
        previewVisible.value = true
        return
      }
    } catch {
      return
    }
  }
  previewUrl.value = await resolvePreviewUrl(row.id)
  previewVisible.value = true
}

function thumbUrl(row: FileItem) {
  if (!row.hasThumbnail) return ''
  return `/api/files/${row.id}/thumbnail?access_token=${tokenParam()}`
}

function openShare(row: FileItem) {
  shareFileId.value = row.type === 'file' ? row.id : null
  shareFolderId.value = row.type === 'folder' ? row.id : null
  shareItemName.value = row.name
  shareVisible.value = true
}

function openMoveCopy(row: FileItem, mode: 'move' | 'copy') {
  moveCopyItem.value = row
  moveCopyMode.value = mode
  moveCopyVisible.value = true
}

async function renameItem(row: FileItem) {
  const { value } = await ElMessageBox.prompt('新名称', '重命名', { inputValue: row.name }).catch(() => ({
    value: null
  }))
  if (!value?.trim() || value.trim() === row.name) return
  try {
    const url = row.type === 'folder' ? `/api/folders/${row.id}/rename` : `/api/files/${row.id}/rename`
    await http.put(url, { name: value.trim() })
    ElMessage.success('重命名成功')
    await refreshAfterChange()
  } catch {
    /* global toast */
  }
}

async function deleteItem(row: FileItem) {
  await ElMessageBox.confirm(`确定删除「${row.name}」？`, '删除', { type: 'warning' })
  try {
    const url = row.type === 'folder' ? `/api/folders/${row.id}` : `/api/files/${row.id}`
    await http.delete(url)
    ElMessage.success('已移入回收站')
    await refreshAfterChange()
  } catch {
    /* global toast */
  }
}

function onWsProgress(data: {
  type?: string
  taskId?: string
  progress?: number
  status?: string
  content?: string
  title?: string
}) {
  if (data.type === 'notification') {
    ElMessage.info(data.content || data.title || '新通知')
    return
  }
  if (!data.taskId) return
  uploadStore.updateProgress(data.taskId, data.progress ?? 0, data.status)
}

const isImage = computed(() => previewType.value.startsWith('image/'))
const isVideo = computed(() => previewType.value.startsWith('video/'))
const isPdf = computed(() => previewType.value.includes('pdf'))
const isOffice = computed(() => !!onlyOfficeConfig.value)

function fileIconColor(row: FileItem): string {
  if (row.type === 'folder') return 'var(--cd-file-folder)'
  const mime = (row.mimeType || '').toLowerCase()
  if (mime.startsWith('image/')) return 'var(--cd-file-image)'
  if (mime.startsWith('video/')) return 'var(--cd-file-video)'
  if (mime.includes('pdf')) return 'var(--cd-file-pdf)'
  if (mime.includes('word') || mime.includes('document')) return 'var(--cd-file-doc)'
  if (mime.includes('sheet') || mime.includes('excel')) return 'var(--cd-file-excel)'
  if (mime.includes('presentation') || mime.includes('powerpoint')) return 'var(--cd-file-ppt)'
  if (mime.includes('zip') || mime.includes('rar') || mime.includes('7z') || mime.includes('tar')) return 'var(--cd-file-archive)'
  return 'var(--cd-file-default)'
}

onMounted(() => {
  fileStore.loadList()
  connectUploadWs(onWsProgress)
})

onUnmounted(disconnectUploadWs)
</script>

<template>
  <div class="cd-page cd-disk-page">
    <!-- 左侧目录树 -->
    <aside v-show="!treeCollapsed" class="cd-disk-tree">
      <div class="cd-disk-tree-head">
        <el-icon :size="16" color="var(--cd-primary)"><FolderOpened /></el-icon>
        <span>文件夹</span>
      </div>
      <div class="cd-disk-tree-body">
        <FolderTree ref="folderTreeRef" :current-id="currentFolderId" @select="fileStore.onTreeSelect" />
      </div>
    </aside>

    <!-- 右侧主内容 -->
    <div class="cd-disk-main" @dragover.prevent @drop="onDrop">
      <div class="cd-disk-panel">
        <div class="cd-disk-toolbar">
          <div class="cd-toolbar-row cd-toolbar-main">
            <div class="cd-toolbar-left">
              <button
                type="button"
                class="cd-tree-toggle"
                :title="treeCollapsed ? '展开目录' : '收起目录'"
                @click="treeCollapsed = !treeCollapsed"
              >
                <el-icon :size="16"><Expand v-if="treeCollapsed" /><Fold v-else /></el-icon>
              </button>
              <el-breadcrumb separator="/">
                <el-breadcrumb-item v-for="(c, i) in breadcrumb" :key="c.id">
                  <a href="#" @click.prevent="fileStore.gotoCrumb(i)">{{ c.name }}</a>
                </el-breadcrumb-item>
              </el-breadcrumb>
            </div>
            <div class="cd-toolbar-actions">
              <el-button type="primary" @click="fileInput?.click()">
                <el-icon><Upload /></el-icon>
                上传文件
              </el-button>
              <el-button @click="folderInput?.click()">
                <el-icon><FolderAdd /></el-icon>
                上传文件夹
              </el-button>
              <el-button @click="createFolder">
                <el-icon><FolderAdd /></el-icon>
                新建文件夹
              </el-button>
            </div>
          </div>
          <div class="cd-toolbar-row cd-toolbar-sub">
            <div class="cd-search-box">
              <el-icon :size="14"><Search /></el-icon>
              <el-input
                v-model="keyword"
                placeholder="搜索文件名"
                clearable
                @keyup.enter="fileStore.loadList()"
              />
            </div>
            <el-select
              v-model="fileType"
              class="cd-type-select"
              placeholder="类型"
              clearable
              @change="fileStore.loadList()"
            >
              <el-option label="全部" value="" />
              <el-option label="图片" value="image" />
              <el-option label="视频" value="video" />
              <el-option label="文档" value="document" />
              <el-option label="压缩包" value="archive" />
            </el-select>
            <div class="cd-view-toggle">
              <button
                class="cd-view-btn"
                :class="{ active: viewMode === 'grid' }"
                title="网格视图"
                @click="viewMode = 'grid'"
              >
                <el-icon :size="16"><Grid /></el-icon>
              </button>
              <button
                class="cd-view-btn"
                :class="{ active: viewMode === 'list' }"
                title="列表视图"
                @click="viewMode = 'list'"
              >
                <el-icon :size="16"><List /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <input ref="fileInput" type="file" multiple hidden @change="onFileChange" />
        <input ref="folderInput" type="file" webkitdirectory multiple hidden @change="onFolderChange" />

        <div class="cd-disk-body">
        <!-- 网格视图 -->
        <FileGridView
          v-if="viewMode === 'grid'"
          :items="items"
          :loading="loading"
          @open="fileStore.enterFolder"
          @download="download"
          @direct-download="directDownload"
          @preview="preview"
          @share="openShare"
          @move="openMoveCopy($event, 'move')"
          @copy="openMoveCopy($event, 'copy')"
          @rename="renameItem"
          @delete="deleteItem"
        >
          <template #empty>
            <div class="cd-disk-empty">
              <div class="cd-disk-empty-icon">
                <el-icon :size="36"><UploadFilled /></el-icon>
              </div>
              <h3>还没有文件</h3>
              <p>拖拽文件到此处，或点击上传开始使用</p>
              <div class="cd-disk-empty-actions">
                <el-button type="primary" @click="fileInput?.click()">
                  <el-icon><Upload /></el-icon>
                  上传文件
                </el-button>
                <el-button @click="createFolder">
                  <el-icon><FolderAdd /></el-icon>
                  新建文件夹
                </el-button>
              </div>
            </div>
          </template>
        </FileGridView>

        <!-- 列表视图 -->
        <div v-else style="margin-top: 16px">
          <el-table v-loading="loading" :data="items" @row-dblclick="fileStore.enterFolder">
            <el-table-column label="名称" min-width="320">
              <template #default="{ row }">
                <div class="cd-name-cell">
                  <img v-if="row.hasThumbnail" :src="thumbUrl(row)" class="cd-thumb" alt="" />
                  <div v-else class="cd-file-icon" :style="{ color: fileIconColor(row) }">
                    <el-icon :size="22">
                      <Folder v-if="row.type === 'folder'" />
                      <Document v-else />
                    </el-icon>
                  </div>
                  <span v-if="row.highlightName" class="cd-name-text" v-html="row.highlightName" />
                  <span v-else class="cd-name-text">{{ row.name }}</span>
                  <el-tag
                    v-if="row.type === 'file' && transcodeLabel(row.transcodeStatus)"
                    size="small"
                    :type="row.transcodeStatus === 'FAILED' ? 'danger' : row.transcodeStatus === 'DONE' ? 'success' : 'info'"
                    class="cd-transcode-tag"
                  >
                    {{ transcodeLabel(row.transcodeStatus) }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="120">
              <template #default="{ row }">
                <span class="cd-cell-text">{{ row.type === 'file' ? fmtSize(row.sizeBytes || 0) : '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="修改时间" width="180">
              <template #default="{ row }">
                <span class="cd-cell-text">{{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <div class="cd-row-actions">
                  <el-button v-if="row.type === 'folder'" link type="primary" @click="fileStore.enterFolder(row)">
                    <el-icon><FolderOpened /></el-icon>打开
                  </el-button>
                  <el-button v-if="row.type === 'folder'" link @click="openShare(row)">
                    <el-icon><Share /></el-icon>
                  </el-button>
                  <template v-else>
                    <el-button link type="primary" @click="download(row)">
                      <el-icon><Download /></el-icon>
                    </el-button>
                    <el-button v-if="row.previewable" link @click="preview(row)">
                      <el-icon><View /></el-icon>
                    </el-button>
                    <el-button link @click="openShare(row)">
                      <el-icon><Share /></el-icon>
                    </el-button>
                  </template>
                  <el-dropdown trigger="click" @command="(cmd: string) => {
                    if (cmd === 'move') openMoveCopy(row, 'move')
                    else if (cmd === 'copy') openMoveCopy(row, 'copy')
                    else if (cmd === 'rename') renameItem(row)
                    else if (cmd === 'delete') deleteItem(row)
                  }">
                    <el-button link>
                      <el-icon><MoreFilled /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="move">
                          <el-icon><Rank /></el-icon>移动
                        </el-dropdown-item>
                        <el-dropdown-item v-if="row.type === 'file'" command="copy">
                          <el-icon><CopyDocument /></el-icon>复制
                        </el-dropdown-item>
                        <el-dropdown-item command="rename">
                          <el-icon><Edit /></el-icon>重命名
                        </el-dropdown-item>
                        <el-dropdown-item command="delete" divided>
                          <el-icon color="#EF4444"><Delete /></el-icon>
                          <span style="color: #EF4444">删除</span>
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        </div>
      </div>

      <!-- 上传进度 -->
      <transition name="fade">
        <el-card v-if="uploadTasks.length" shadow="never" class="cd-upload-card">
          <template #header>
            <div class="cd-upload-header">
              <el-icon :size="16" color="var(--cd-primary)"><Upload /></el-icon>
              <span>上传进度</span>
              <span class="cd-upload-count">{{ uploadTasks.length }} 个任务</span>
            </div>
          </template>
          <div v-for="t in uploadTasks" :key="t.id" class="cd-upload-task">
            <div class="cd-task-info">
              <span class="cd-task-name">{{ t.name }}</span>
              <el-tag
                v-if="t.status === 'instant'"
                size="small"
                type="success"
                class="cd-instant-tag"
              >
                <el-icon><Lightning /></el-icon>
                秒传
              </el-tag>
              <el-tag
                v-if="t.status === 'done'"
                size="small"
                type="success"
              >
                <el-icon><Check /></el-icon>
                完成
              </el-tag>
            </div>
            <el-progress
              :percentage="Math.round(t.progress * 100)"
              :status="t.status === 'error' ? 'exception' : t.status === 'done' ? 'success' : undefined"
              :stroke-width="6"
            />
          </div>
        </el-card>
      </transition>

      <ShareDialog
        v-model="shareVisible"
        :file-id="shareFileId"
        :folder-id="shareFolderId"
        :item-name="shareItemName"
      />
      <MoveCopyDialog
        v-model="moveCopyVisible"
        :mode="moveCopyMode"
        :item-type="moveCopyItem?.type || 'file'"
        :item-id="moveCopyItem?.id ?? null"
        :item-name="moveCopyItem?.name || ''"
        @done="refreshAfterChange"
      />

      <el-dialog v-model="previewVisible" :title="previewName" width="90%" destroy-on-close top="4vh">
        <OnlyOfficeEditor
          v-if="isOffice && onlyOfficeConfig"
          :document-server-url="onlyOfficeConfig.documentServerUrl"
          :config="onlyOfficeConfig.config"
        />
        <img v-else-if="isImage" :src="previewUrl" class="cd-preview-media" alt="preview" />
        <VideoPreview v-else-if="isVideo" :src="previewUrl" />
        <PdfPreview v-else-if="isPdf" :src="previewUrl" />
        <el-empty v-else description="暂不支持该类型预览" />
      </el-dialog>
    </div>
  </div>
</template>

<style scoped>
/* ============================================

   布局

   ============================================ */

.cd-disk-page {
  flex-direction: row;
  background: var(--theme-bg, var(--cd-bg));
}

.cd-disk-tree {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--theme-border, var(--cd-border));
  background: #fff;
}

.cd-disk-tree-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--cd-text-secondary);
  border-bottom: 1px solid var(--theme-border, var(--cd-border-light));
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.cd-disk-tree-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 6px 4px 12px;
}

.cd-disk-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}

.cd-disk-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cd-disk-toolbar {
  flex-shrink: 0;
  border-bottom: 1px solid var(--theme-border, var(--cd-border-light));
  background: #fff;
}

.cd-toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 20px;
}

.cd-toolbar-main {
  min-height: 52px;
}

.cd-toolbar-sub {
  min-height: 44px;
  padding-bottom: 10px;
  border-top: 1px solid color-mix(in srgb, var(--theme-border) 55%, transparent);
  gap: 10px;
  justify-content: flex-start;
}

.cd-disk-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 20px 24px;
  background: color-mix(in srgb, var(--theme-bg) 35%, #fff);
}

.cd-tree-toggle {
  width: 32px;
  height: 32px;
  border: 1px solid var(--theme-border, var(--cd-border));
  border-radius: 8px;
  background: #fff;
  color: var(--cd-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: var(--cd-transition-fast);
}

.cd-tree-toggle:hover {
  border-color: var(--cd-primary);
  color: var(--cd-primary);
  background: var(--theme-primary-muted);
}

.cd-toolbar-actions {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  flex-shrink: 0;
}

.cd-type-select {
  width: 112px;
  flex-shrink: 0;
}

.cd-disk-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  text-align: center;
  padding: 40px 20px;
}

.cd-disk-empty-icon {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: var(--theme-primary-muted);
  color: var(--cd-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.cd-disk-empty h3 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--cd-text-primary);
}

.cd-disk-empty p {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--cd-text-secondary);
}

.cd-disk-empty-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

/* ============================================

   工具栏

   ============================================ */

.cd-toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.cd-toolbar-left :deep(.el-breadcrumb) {
  min-width: 0;
  line-height: 1.4;
}

.cd-search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  width: min(320px, 100%);
  flex-shrink: 0;
  background: var(--cd-bg);
  border: 1px solid var(--cd-border);
  border-radius: var(--cd-radius);
  padding: 0 10px;
  transition: var(--cd-transition-fast);
}

.cd-search-box:focus-within {
  border-color: var(--cd-primary);
  box-shadow: 0 0 0 3px rgba(79, 124, 255, 0.1);
}

.cd-search-box .el-icon {
  color: var(--cd-text-placeholder);
  flex-shrink: 0;
}

.cd-search-box :deep(.el-input__wrapper) {
  box-shadow: none !important;
  border: none !important;
  background: transparent !important;
  padding: 0 !important;
}

/* 视图切换 */

.cd-view-toggle {
  display: flex;
  background: var(--cd-bg);
  border: 1px solid var(--cd-border);
  border-radius: var(--cd-radius);
  padding: 2px;
}

.cd-view-btn {
  width: 32px;
  height: 28px;
  border: none;
  background: none;
  border-radius: 5px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--cd-text-placeholder);
  transition: var(--cd-transition-fast);
}

.cd-view-btn.active {
  background: var(--cd-primary);
  color: #fff;
  box-shadow: 0 1px 3px var(--theme-primary-muted-strong);
}

.cd-view-btn:not(.active):hover {
  color: var(--cd-text-secondary);
  background: rgba(0, 0, 0, 0.03);
}

.cd-toolbar-divider {
  width: 1px;
  height: 24px;
  background: var(--cd-border);
  margin: 0 4px;
}

/* ============================================

   列表视图 - 名称单元格

   ============================================ */

.cd-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cd-thumb {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: var(--cd-radius);
  flex-shrink: 0;
}

.cd-file-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--cd-radius);
  background: var(--cd-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cd-name-text {
  font-weight: 500;
  color: var(--cd-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cd-transcode-tag {
  margin-left: 8px;
  flex-shrink: 0;
}

.cd-cell-text {
  color: var(--cd-text-secondary);
  font-size: 13px;
}

/* 行操作按钮 */

.cd-row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

/* ============================================

   上传进度

   ============================================ */

.cd-upload-card {
  flex-shrink: 0;
  margin: 0;
  border-left: none;
  border-top: 1px solid var(--theme-border, var(--cd-border-light));
  border-radius: 0 !important;
  box-shadow: none !important;
}

.cd-upload-card :deep(.el-card__header) {
  padding: 12px 16px !important;
}

.cd-upload-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}

.cd-upload-count {
  font-size: 12px;
  color: var(--cd-text-placeholder);
  font-weight: 400;
  margin-left: auto;
}

.cd-upload-task {
  margin-bottom: 16px;
}

.cd-upload-task:last-child {
  margin-bottom: 0;
}

.cd-task-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.cd-task-name {
  font-size: 13px;
  color: var(--cd-text-primary);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.cd-instant-tag {
  animation: pulse 1.5s ease-in-out;
}

/* ============================================

   预览

   ============================================ */

.cd-preview-media {
  max-width: 100%;
  max-height: 75vh;
  display: block;
  margin: 0 auto;
  border-radius: var(--cd-radius);
}
</style>
