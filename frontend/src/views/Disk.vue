<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import http, { TOKEN_KEY } from '@/api/http'
import { fmtSize, fileIconColor, transcodeLabel } from '@/utils/fileMeta'
import { fileCoverKind, fileCoverUrl } from '@/utils/fileCover'
import { useFileStore, type FileItem } from '@/stores/file'
import { useTransferStore, promptCreateFolder } from '@/stores/transfer'
import ShareDialog from '@/components/ShareDialog.vue'
import MoveCopyDialog from '@/components/MoveCopyDialog.vue'
import FolderTree from '@/components/FolderTree.vue'
import FileGridView from '@/components/FileGridView.vue'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'
import PdfPreview from '@/components/PdfPreview.vue'
import VideoPreview from '@/components/VideoPreview.vue'
import TextPreview from '@/components/TextPreview.vue'
import { isTextFile } from '@/utils/filePreview'
import { connectUploadWs, disconnectUploadWs } from '@/utils/ws'
import { downloadZip } from '@/utils/download'

const fileStore = useFileStore()
const transferStore = useTransferStore()
const { currentFolderId, breadcrumb, items, loading, keyword, fileType } = storeToRefs(fileStore)

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
const dragOver = ref(false)

const fileInput = ref<HTMLInputElement | null>(null)
const folderInput = ref<HTMLInputElement | null>(null)

const selectedItems = ref<FileItem[]>([])
const tableRef = ref<any>(null)

const selectedIds = computed({
  get: () => selectedItems.value.map(item => item.id),
  set: (ids) => {
    selectedItems.value = items.value.filter(item => ids.includes(item.id))
  }
})

function handleSelectionChange(val: FileItem[]) {
  selectedItems.value = val
}

function clearSelection() {
  selectedItems.value = []
  if (tableRef.value) {
    tableRef.value.clearSelection()
  }
}

watch(selectedItems, (newVal) => {
  if (!tableRef.value) return
  const rows = tableRef.value.data || []
  rows.forEach((row: any) => {
    const shouldSelect = newVal.some(item => item.id === row.id)
    if (tableRef.value.getSelection?.().includes(row) !== shouldSelect) {
      tableRef.value.toggleRowSelection(row, shouldSelect)
    }
  })
}, { deep: true })

watch(currentFolderId, () => {
  clearSelection()
})

function handleBatchDownload() {
  const folders = selectedItems.value.filter(i => i.type === 'folder').map(i => i.id)
  const files = selectedItems.value.filter(i => i.type === 'file').map(i => i.id)
  if (folders.length === 0 && files.length === 0) return
  let url = `/api/files/download/zip?access_token=${tokenParam()}`
  if (folders.length > 0) {
    url += `&folderIds=${folders.join(',')}`
  }
  if (files.length > 0) {
    url += `&fileIds=${files.join(',')}`
  }
  downloadZip(url)
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedItems.value.length} 个项目吗？`, '批量删除', { type: 'warning' })
  try {
    for (const item of selectedItems.value) {
      const url = item.type === 'folder' ? `/api/folders/${item.id}` : `/api/files/${item.id}`
      await http.delete(url)
    }
    ElMessage.success('批量删除成功')
    clearSelection()
    await refreshAfterChange()
  } catch {
    /* error */
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
  await transferStore.processFiles(files, currentFolderId.value, refreshAfterChange)
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
  dragOver.value = false
  if (e.dataTransfer?.files?.length) void processFiles(Array.from(e.dataTransfer.files))
}

function onDragEnter() {
  dragOver.value = true
}

function onDragLeave(e: DragEvent) {
  const related = e.relatedTarget as Node | null
  if (!related || !(e.currentTarget as HTMLElement).contains(related)) {
    dragOver.value = false
  }
}

function download(row: FileItem) {
  if (row.type === 'folder') {
    downloadZip(`/api/files/download/zip?folderIds=${row.id}&access_token=${tokenParam()}`)
    return
  }
  transferStore.addDownloadTask(row.id, row.name, row.sizeBytes || 0)
}

async function directDownload(row: FileItem) {
  download(row)
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
  transferStore.updateProgress(data.taskId, data.progress ?? 0, data.status)
}

const isImage = computed(() => previewType.value.startsWith('image/'))
const isVideo = computed(() => previewType.value.startsWith('video/'))
const isPdf = computed(() => previewType.value.includes('pdf'))
const isText = computed(() => isTextFile(previewType.value, previewName.value))
const isOffice = computed(() => !!onlyOfficeConfig.value)

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
    <div
      class="cd-disk-main"
      :class="{ 'is-dragover': dragOver }"
      @dragover.prevent
      @dragenter.prevent="onDragEnter"
      @dragleave="onDragLeave"
      @drop="onDrop"
    >
      <div v-if="dragOver" class="cd-drop-overlay">
        <div class="cd-drop-overlay-inner">
          <el-icon :size="40"><UploadFilled /></el-icon>
          <p>释放文件以上传到当前目录</p>
        </div>
      </div>
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
            <span class="cd-item-count">
              共 <strong>{{ items.length }}</strong> 项
            </span>
            <div class="cd-search-box">
              <el-icon :size="15"><Search /></el-icon>
              <el-input
                v-model="keyword"
                placeholder="搜索文件名"
                clearable
                @keyup.enter="fileStore.loadList()"
              />
            </div>
            <div class="cd-toolbar-filters">
              <el-select
                v-model="fileType"
                class="cd-type-select"
                placeholder="类型"
                clearable
                @change="fileStore.loadList()"
                popper-class="cd-type-select-dropdown"
              >
                <template #prefix>
                  <el-icon v-if="fileType === 'folder'" class="cd-select-prefix-icon"><Folder /></el-icon>
                  <el-icon v-else-if="fileType === 'image'" class="cd-select-prefix-icon"><Picture /></el-icon>
                  <el-icon v-else-if="fileType === 'video'" class="cd-select-prefix-icon"><VideoPlay /></el-icon>
                  <el-icon v-else-if="fileType === 'document'" class="cd-select-prefix-icon"><Document /></el-icon>
                  <el-icon v-else-if="fileType === 'archive'" class="cd-select-prefix-icon"><Box /></el-icon>
                  <el-icon v-else class="cd-select-prefix-icon"><Files /></el-icon>
                </template>
                <el-option value="" label="全部">
                  <div class="cd-option-item">
                    <el-icon class="cd-option-icon"><Files /></el-icon>
                    <span>全部</span>
                  </div>
                </el-option>
                <el-option value="folder" label="文件夹">
                  <div class="cd-option-item">
                    <el-icon class="cd-option-icon"><Folder /></el-icon>
                    <span>文件夹</span>
                  </div>
                </el-option>
                <el-option value="image" label="图片">
                  <div class="cd-option-item">
                    <el-icon class="cd-option-icon"><Picture /></el-icon>
                    <span>图片</span>
                  </div>
                </el-option>
                <el-option value="video" label="视频">
                  <div class="cd-option-item">
                    <el-icon class="cd-option-icon"><VideoPlay /></el-icon>
                    <span>视频</span>
                  </div>
                </el-option>
                <el-option value="document" label="文档">
                  <div class="cd-option-item">
                    <el-icon class="cd-option-icon"><Document /></el-icon>
                    <span>文档</span>
                  </div>
                </el-option>
                <el-option value="archive" label="压缩包">
                  <div class="cd-option-item">
                    <el-icon class="cd-option-icon"><Box /></el-icon>
                    <span>压缩包</span>
                  </div>
                </el-option>
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
        </div>

        <input ref="fileInput" type="file" multiple hidden @change="onFileChange" />
        <input ref="folderInput" type="file" webkitdirectory multiple hidden @change="onFolderChange" />

        <div class="cd-disk-body">
        <!-- 网格视图 -->
        <FileGridView
          v-if="viewMode === 'grid'"
          v-model:selectedIds="selectedIds"
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
        <div v-else class="cd-disk-list-wrap">
          <el-table
            ref="tableRef"
            v-loading="loading"
            :data="items"
            class="cd-disk-table"
            @row-dblclick="fileStore.enterFolder"
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="55" align="center" />
            <el-table-column label="名称" min-width="320" header-align="center">
              <template #default="{ row }">
                <div class="cd-name-cell">
                  <img
                    v-if="fileCoverKind(row) === 'image'"
                    :src="fileCoverUrl(row)"
                    class="cd-thumb"
                    alt=""
                    loading="lazy"
                  />
                  <video
                    v-else-if="fileCoverKind(row) === 'video'"
                    :src="fileCoverUrl(row)"
                    class="cd-thumb cd-list-thumb-video"
                    muted
                    preload="metadata"
                    playsinline
                  />
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
            <el-table-column label="大小" width="120" align="center" header-align="center">
              <template #default="{ row }">
                <span class="cd-cell-text">{{ row.type === 'file' ? fmtSize(row.sizeBytes || 0) : '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="修改时间" width="180" align="center" header-align="center">
              <template #default="{ row }">
                <span class="cd-cell-text">{{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="168" fixed="right" align="center" header-align="center">
              <template #default="{ row }">
                <div class="cd-row-actions">
                  <el-tooltip v-if="row.type === 'folder'" content="打开" placement="top">
                    <button type="button" class="cd-list-action-btn" @click="fileStore.enterFolder(row)">
                      <el-icon><FolderOpened /></el-icon>
                    </button>
                  </el-tooltip>
                  <template v-else>
                    <el-tooltip content="下载" placement="top">
                      <button type="button" class="cd-list-action-btn" @click="download(row)">
                        <el-icon><Download /></el-icon>
                      </button>
                    </el-tooltip>
                    <el-tooltip v-if="row.previewable" content="预览" placement="top">
                      <button type="button" class="cd-list-action-btn" @click="preview(row)">
                        <el-icon><View /></el-icon>
                      </button>
                    </el-tooltip>
                  </template>
                  <el-tooltip content="分享" placement="top">
                    <button type="button" class="cd-list-action-btn" @click="openShare(row)">
                      <el-icon><Share /></el-icon>
                    </button>
                  </el-tooltip>
                  <el-dropdown trigger="click" @command="(cmd: string) => {
                    if (cmd === 'move') openMoveCopy(row, 'move')
                    else if (cmd === 'copy') openMoveCopy(row, 'copy')
                    else if (cmd === 'rename') renameItem(row)
                    else if (cmd === 'delete') deleteItem(row)
                    else if (cmd === 'download') download(row)
                  }">
                    <button type="button" class="cd-list-action-btn" aria-label="更多">
                      <el-icon><MoreFilled /></el-icon>
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="move">
                          <el-icon><Rank /></el-icon>移动
                        </el-dropdown-item>
                        <el-dropdown-item v-if="row.type === 'file'" command="copy">
                          <el-icon><CopyDocument /></el-icon>复制
                        </el-dropdown-item>
                        <el-dropdown-item v-if="row.type === 'folder'" command="download">
                          <el-icon><Download /></el-icon>打包下载
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

      <!-- 上传进度由全局 TransferPanel 接管 -->

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

      <el-dialog v-model="previewVisible" :title="previewName" width="90%" destroy-on-close top="4vh" class="cd-preview-dialog">
        <OnlyOfficeEditor
          v-if="isOffice && onlyOfficeConfig"
          :document-server-url="onlyOfficeConfig.documentServerUrl"
          :config="onlyOfficeConfig.config"
        />
        <img v-else-if="isImage" :src="previewUrl" class="cd-preview-media" alt="preview" />
        <VideoPreview v-else-if="isVideo" :src="previewUrl" />
        <PdfPreview v-else-if="isPdf" :src="previewUrl" />
        <TextPreview v-else-if="isText" :src="previewUrl" />
        <el-empty v-else description="暂不支持该类型预览" />
      </el-dialog>

      <!-- 悬浮批量操作底栏 -->
      <transition name="cd-fade-slide">
        <div v-if="selectedItems.length > 0" class="cd-batch-bar">
          <div class="cd-batch-info">
            已选择 <span class="cd-batch-count">{{ selectedItems.length }}</span> 项
          </div>
          <div class="cd-batch-actions">
            <el-button type="primary" @click="handleBatchDownload">
              <el-icon><Download /></el-icon>
              打包下载
            </el-button>
            <el-button type="danger" plain @click="handleBatchDelete">
              <el-icon><Delete /></el-icon>
              批量删除
            </el-button>
            <el-button @click="clearSelection">取消</el-button>
          </div>
        </div>
      </transition>
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
  padding: 14px 16px;
  font-size: 12px;
  font-weight: 700;
  color: var(--cd-text-secondary);
  border-bottom: 1px solid var(--theme-border, var(--cd-border-light));
  letter-spacing: 0.06em;
  text-transform: uppercase;
  background: linear-gradient(180deg, #fff 0%, color-mix(in srgb, var(--theme-bg) 35%, #fff) 100%);
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
  position: relative;
}

.cd-disk-main.is-dragover .cd-disk-panel {
  filter: blur(1px);
  opacity: 0.72;
  pointer-events: none;
}

.cd-drop-overlay {
  position: absolute;
  inset: 12px;
  z-index: 20;
  border-radius: var(--cd-radius-lg);
  border: 2px dashed var(--cd-primary);
  background: color-mix(in srgb, var(--cd-primary) 8%, rgba(255, 255, 255, 0.92));
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.cd-drop-overlay-inner {
  text-align: center;
  color: var(--cd-primary);
}

.cd-drop-overlay-inner p {
  margin: 12px 0 0;
  font-size: 15px;
  font-weight: 600;
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
  min-height: 56px;
}

.cd-toolbar-sub {
  min-height: 52px;
  padding: 10px 20px 14px;
  gap: 16px;
}

.cd-item-count {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--cd-text-secondary);
  white-space: nowrap;
}

.cd-item-count strong {
  color: var(--cd-text-primary);
  font-weight: 600;
}

.cd-toolbar-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.cd-disk-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 20px 20px 28px;
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
  width: 124px;
  flex-shrink: 0;
}

.cd-type-select :deep(.el-select__wrapper) {
  background-color: var(--cd-bg);
  box-shadow: 0 0 0 1px var(--cd-border) inset !important;
  border-radius: 10px;
  height: 36px;
  min-height: 36px;
  padding: 0 10px 0 12px;
  transition: var(--cd-transition-fast);
}

.cd-type-select :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--cd-primary-light) inset !important;
}

.cd-type-select :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--cd-primary) inset, 0 0 0 3px var(--theme-primary-muted), 0 0 12px var(--theme-primary-muted) !important;
}

.cd-type-select :deep(.el-select__placeholder) {
  color: var(--cd-text-primary);
  font-weight: 500;
  font-size: 14px;
}

.cd-type-select :deep(.el-select__selected-item) {
  color: var(--cd-text-primary);
  font-weight: 500;
  font-size: 14px;
}

.cd-select-prefix-icon {
  font-size: 16px;
  color: var(--cd-primary);
  margin-right: 6px;
}

/* 下拉菜单项样式 */
.cd-option-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cd-option-icon {
  font-size: 16px;
  color: var(--cd-text-secondary);
  transition: var(--cd-transition-fast);
}

:deep(.cd-type-select-dropdown) {
  border-radius: var(--cd-radius-lg) !important;
  padding: 6px !important;
  box-shadow: var(--cd-shadow-lg) !important;
  border: 1px solid var(--cd-border-light) !important;
}

:deep(.cd-type-select-dropdown .el-select-dropdown__item) {
  border-radius: var(--cd-radius) !important;
  margin: 2px 0 !important;
  height: 36px !important;
  line-height: 36px !important;
  color: var(--cd-text-regular) !important;
  font-weight: 500 !important;
}

:deep(.cd-type-select-dropdown .el-select-dropdown__item.is-hovering) {
  background-color: var(--theme-primary-muted) !important;
  color: var(--cd-primary) !important;
}

:deep(.cd-type-select-dropdown .el-select-dropdown__item.is-selected) {
  background-color: var(--theme-primary-muted-strong) !important;
  color: var(--cd-primary) !important;
  font-weight: 600 !important;
}

:deep(.cd-type-select-dropdown .el-select-dropdown__item.is-hovering .cd-option-icon) {
  color: var(--cd-primary) !important;
}

:deep(.cd-type-select-dropdown .el-select-dropdown__item.is-selected .cd-option-icon) {
  color: var(--cd-primary) !important;
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
  width: 76px;
  height: 76px;
  border-radius: 22px;
  background: var(--theme-primary-muted);
  color: var(--cd-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
  animation: gentleFloat 3s ease-in-out infinite;
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
  gap: 8px;
  flex: 1;
  min-width: 0;
  max-width: none;
  height: 36px;
  background: var(--cd-bg);
  border: 1px solid var(--cd-border);
  border-radius: 10px;
  padding: 0 12px;
  transition: var(--cd-transition-fast);
}

.cd-search-box:focus-within {
  border-color: var(--cd-primary);
  box-shadow: 0 0 0 3px var(--theme-primary-muted), 0 0 12px var(--theme-primary-muted);
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
  border-radius: 10px;
  padding: 3px;
  gap: 2px;
}

.cd-view-btn {
  width: 36px;
  height: 30px;
  border: none;
  background: none;
  border-radius: 7px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--cd-text-placeholder);
  transition: var(--cd-transition);
}

.cd-view-btn.active {
  background: #1e293b;
  color: #fff;
  box-shadow: none;
}

.cd-view-btn:not(.active):hover {
  color: var(--cd-text-secondary);
  background: rgba(0, 0, 0, 0.04);
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
  border-radius: 10px;
  flex-shrink: 0;
  box-shadow: var(--cd-shadow-sm);
}

.cd-list-thumb-video {
  pointer-events: none;
  background: #000;
}

.cd-file-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--cd-bg) 0%, color-mix(in srgb, var(--theme-primary) 6%, var(--cd-bg)) 100%);
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

/* 列表视图 */

.cd-disk-list-wrap {
  margin-top: 16px;
}

.cd-disk-table :deep(.el-table__header th .cell) {
  font-weight: 600;
  color: var(--cd-text-primary);
}

.cd-disk-table :deep(.el-table__row) {
  transition: background-color var(--cd-transition-fast);
}

.cd-disk-table :deep(.el-table__row:hover) {
  background-color: var(--cd-bg-surface) !important;
}

/* 行操作按钮 */

.cd-row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 3px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--cd-bg) 80%, var(--cd-border-light));
}

.cd-list-action-btn {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--cd-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 16px;
  transition: background-color var(--cd-transition-fast), color var(--cd-transition-fast), transform var(--cd-transition-fast);
}

.cd-list-action-btn:hover {
  background: #fff;
  color: var(--cd-primary);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

/* 预览对话框 */
.cd-preview-dialog :deep(.el-dialog__header) {
  padding-bottom: 8px !important;
}

.cd-preview-dialog :deep(.el-dialog__body) {
  padding-top: 0 !important;
}

.cd-preview-media {
  max-width: 100%;
  max-height: 75vh;
  display: block;
  margin: 0 auto;
  border-radius: var(--cd-radius);
}

/* 悬浮批量操作底栏 */
.cd-batch-bar {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(15, 23, 42, 0.08);
  padding: 12px 24px;
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.12);
  display: flex;
  align-items: center;
  gap: 32px;
  z-index: 1000;
  pointer-events: auto;
  transition: all var(--cd-transition-bounce);
}

.cd-batch-info {
  font-size: 14px;
  color: var(--cd-text-secondary);
  font-weight: 500;
}

.cd-batch-count {
  color: var(--cd-primary);
  font-weight: 700;
  font-size: 16px;
  margin: 0 4px;
}

.cd-batch-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 进出场动画 */
.cd-fade-slide-enter-active,
.cd-fade-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.cd-fade-slide-enter-from,
.cd-fade-slide-leave-to {
  opacity: 0;
  transform: translate(-50%, 20px);
}
</style>
