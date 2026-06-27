<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, onActivated, onDeactivated } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { resolveFilePreviewUrl } from '@/utils/fileUrl'
import { useConfirmDialogStore } from '@/stores/confirmDialog'
import { usePromptDialogStore } from '@/stores/promptDialog'
import { fmtSize, fileIconColor, transcodeLabel } from '@/utils/fileMeta'
import { fileCoverKind, fileCoverUrl, fileIsVideoCover } from '@/utils/fileCover'
import { useFileStore, type FileItem } from '@/stores/file'
import { useTransferStore, promptCreateFolder } from '@/stores/transfer'
import { useStorageStore } from '@/stores/storage'
import ShareDialog from '@/components/ShareDialog.vue'
import MoveCopyDialog from '@/components/MoveCopyDialog.vue'
import FolderTree from '@/components/FolderTree.vue'
import FileGridView from '@/components/FileGridView.vue'
import CachedCover from '@/components/CachedCover.vue'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'
import PdfPreview from '@/components/PdfPreview.vue'
import VideoPreview from '@/components/VideoPreview.vue'
import TextPreview from '@/components/TextPreview.vue'
import { isTextFile } from '@/utils/filePreview'
import { connectUploadWs, disconnectUploadWs, type WsMessage } from '@/utils/ws'
import { downloadZip } from '@/utils/download'
import FolderTypeIcon from '@/components/FolderTypeIcon.vue'
import { sanitizeHighlight } from '@/utils/sanitize'

defineOptions({ name: 'Disk' })

const fileStore = useFileStore()
const transferStore = useTransferStore()
const confirmDialog = useConfirmDialogStore()
const promptDialog = usePromptDialogStore()
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
const videoPreviewRef = ref<InstanceType<typeof VideoPreview> | null>(null)
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
  const params: string[] = []
  if (folders.length > 0) params.push(`folderIds=${folders.join(',')}`)
  if (files.length > 0) params.push(`fileIds=${files.join(',')}`)
  downloadZip(`/api/files/download/zip?${params.join('&')}`)
}

async function handleBatchDelete() {
  const ok = await confirmDialog.open({
    title: '批量删除',
    message: `确定要批量删除选中的 ${selectedItems.value.length} 个项目吗？此操作将移入回收站！`,
    confirmText: '删除',
    danger: true
  })
  if (!ok) return
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

async function refreshAfterChange() {
  folderTreeRef.value?.reload()
  fileStore.markListStale()
  await fileStore.loadList()
  void useStorageStore().refresh()
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
    downloadZip(`/api/files/download/zip?folderIds=${row.id}`)
    return
  }
  transferStore.addDownloadTask(row.id, row.name, row.sizeBytes || 0)
}

async function directDownload(row: FileItem) {
  download(row)
}

async function resolvePreviewUrl(fileId: number): Promise<string> {
  return resolveFilePreviewUrl(fileId)
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

/** 网格卡片单击：进入文件夹 / 预览文件 / 非可预览则下载 */
function handleGridOpen(row: FileItem) {
  if (row.type === 'folder') {
    fileStore.enterFolder(row)
  } else if (row.previewable) {
    preview(row)
  } else {
    download(row)
  }
}

function openMoveCopy(row: FileItem, mode: 'move' | 'copy') {
  moveCopyItem.value = row
  moveCopyMode.value = mode
  moveCopyVisible.value = true
}

async function renameItem(row: FileItem) {
  const value = await promptDialog.open({
    title: '重命名',
    defaultValue: row.name,
    confirmText: '确定',
    icon: 'edit',
    maxlength: 255
  })
  if (!value || value === row.name) return
  try {
    const url = row.type === 'folder' ? `/api/folders/${row.id}/rename` : `/api/files/${row.id}/rename`
    await http.put(url, { name: value })
    ElMessage.success('重命名成功')
    await refreshAfterChange()
  } catch {
    /* global toast */
  }
}

async function deleteItem(row: FileItem) {
  const ok = await confirmDialog.open({
    title: '删除项目',
    message: `确定删除「${row.name}」？此操作将移入回收站！`,
    confirmText: '删除',
    danger: true
  })
  if (!ok) return
  try {
    const url = row.type === 'folder' ? `/api/folders/${row.id}` : `/api/files/${row.id}`
    await http.delete(url)
    ElMessage.success('已移入回收站')
    await refreshAfterChange()
  } catch {
    /* global toast */
  }
}

function onWsProgress(data: WsMessage) {
  if (data.type === 'notification') {
    if (data.notifyType === 'TRANSCODE_DONE') {
      fileStore.onTranscodeEvent(data.refId)
      return
    }
    ElMessage.info(data.content || data.title || '新通知')
    return
  }
  if (!data.taskId) return
  transferStore.updateProgress(data.taskId, data.progress ?? 0, data.status)
}

let transcodePollTimer: ReturnType<typeof setInterval> | null = null

function stopTranscodePoll() {
  if (transcodePollTimer) {
    clearInterval(transcodePollTimer)
    transcodePollTimer = null
  }
}

function syncTranscodePoll() {
  if (!fileStore.hasActiveTranscode(items.value)) {
    stopTranscodePoll()
    return
  }
  if (transcodePollTimer) return
  transcodePollTimer = setInterval(() => {
    if (!fileStore.hasActiveTranscode(items.value)) {
      stopTranscodePoll()
      return
    }
    void fileStore.loadList()
  }, 5000)
}

watch(items, () => syncTranscodePoll(), { deep: true })

const isImage = computed(() => previewType.value.startsWith('image/'))
const isVideo = computed(() => previewType.value.startsWith('video/'))
const isPdf = computed(() => previewType.value.includes('pdf'))
const isText = computed(() => isTextFile(previewType.value, previewName.value))
const isOffice = computed(() => !!onlyOfficeConfig.value)

const isArchive = (row: FileItem) => {
  if (row.type !== 'file') return false
  const name = row.name || ''
  const dot = name.lastIndexOf('.')
  const ext = dot > 0 ? name.substring(dot + 1).toLowerCase() : ''
  return ['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)
}

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

onMounted(() => {
  if (!fileStore.listInitialized) {
    fileStore.loadList().then(() => syncTranscodePoll())
  } else {
    syncTranscodePoll()
  }
  connectUploadWs(onWsProgress)
  document.addEventListener('fullscreenchange', handleDialogFullscreenChange)
})

onActivated(() => {
  fileStore.loadList()
  syncTranscodePoll()
})

onDeactivated(() => {
  stopTranscodePoll()
})

onUnmounted(() => {
  stopTranscodePoll()
  disconnectUploadWs()
  document.removeEventListener('fullscreenchange', handleDialogFullscreenChange)
})
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
          @open="handleGridOpen"
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
              <div class="cd-disk-empty-icon-wrapper">
                <div class="cd-disk-empty-icon-bg" />
                <div class="cd-disk-empty-icon is-clickable" @click="fileInput?.click()">
                  <el-icon :size="32"><UploadFilled /></el-icon>
                </div>
              </div>
              <h3>还没有文件</h3>
              <p>拖拽文件到此处，或点击上传开始使用</p>
              <div class="cd-disk-empty-actions">
                <el-button type="primary" class="cd-disk-empty-btn primary-btn" @click="fileInput?.click()">
                  <el-icon><Upload /></el-icon>
                  上传文件
                </el-button>
                <el-button class="cd-disk-empty-btn default-btn" @click="folderInput?.click()">
                  <el-icon><FolderAdd /></el-icon>
                  上传文件夹
                </el-button>
                <el-button class="cd-disk-empty-btn default-btn" @click="createFolder">
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
                  <div v-if="fileCoverKind(row) === 'image'" class="cd-thumb-wrap">
                    <CachedCover
                      :file-id="row.id"
                      :src="fileCoverUrl(row)"
                      :has-thumbnail="row.hasThumbnail"
                      img-class="cd-thumb"
                    />
                    <span v-if="fileIsVideoCover(row)" class="cd-thumb-play">▶</span>
                  </div>
                  <div v-else class="cd-file-icon" :style="{ color: fileIconColor(row) }">
                    <FolderTypeIcon v-if="row.type === 'folder'" :size="24" />
                    <FolderTypeIcon v-else-if="isArchive(row)" :archive="true" :size="24" />
                    <el-icon v-else :size="22">
                      <Document />
                    </el-icon>
                  </div>
                  <span v-if="row.highlightName" class="cd-name-text" v-html="sanitizeHighlight(row.highlightName)" />
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
          v-if="previewVisible && !isVideo"
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

        <OnlyOfficeEditor
          v-if="isOffice && onlyOfficeConfig"
          :document-server-url="onlyOfficeConfig.documentServerUrl"
          :config="onlyOfficeConfig.config"
        />
        <div v-else-if="isImage" class="cd-preview-image-wrap">
          <img :src="previewUrl" class="cd-preview-media" alt="preview" />
        </div>
        <VideoPreview
          v-else-if="isVideo"
          ref="videoPreviewRef"
          :key="previewUrl"
          :src="previewUrl"
        />
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
  border-radius: var(--cd-radius-full);
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
  position: relative;
  background: radial-gradient(circle at center, color-mix(in srgb, var(--cd-primary) 4%, transparent) 0%, transparent 70%);
}

.cd-disk-empty-icon-wrapper {
  position: relative;
  width: 100px;
  height: 100px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cd-disk-empty-icon-bg {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, var(--cd-primary) 15%, transparent) 0%, transparent 75%);
  animation: diskPulseGlow 3s ease-in-out infinite;
}

@keyframes diskPulseGlow {
  0%, 100% { transform: scale(0.9); opacity: 0.7; }
  50% { transform: scale(1.1); opacity: 1; }
}

.cd-disk-empty-icon {
  position: relative;
  z-index: 1;
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--cd-primary) 8%, transparent) 0%, color-mix(in srgb, var(--cd-primary) 12%, transparent) 100%);
  border: 1px solid color-mix(in srgb, var(--cd-primary) 18%, transparent);
  color: var(--cd-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 
    0 10px 24px color-mix(in srgb, var(--cd-primary) 6%, transparent), 
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), background-color 0.3s;
}

.cd-disk-empty-icon.is-clickable {
  cursor: pointer;
}

.cd-disk-empty-icon:hover {
  transform: translateY(-4px) scale(1.05);
  background: linear-gradient(135deg, color-mix(in srgb, var(--cd-primary) 12%, transparent) 0%, color-mix(in srgb, var(--cd-primary) 18%, transparent) 100%);
  box-shadow: 
    0 12px 30px color-mix(in srgb, var(--cd-primary) 12%, transparent), 
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.cd-disk-empty h3 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: 0.5px;
}

.cd-disk-empty p {
  margin: 0 0 20px;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  letter-spacing: 0.2px;
}

.cd-disk-empty-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
}

.cd-disk-empty-btn {
  border-radius: var(--cd-radius-full) !important;
  font-weight: 600 !important;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1) !important;
  height: 38px !important;
  padding: 0 18px !important;
}

.cd-disk-empty-btn.primary-btn {
  background: var(--cd-primary-gradient) !important;
  border: none !important;
  color: #ffffff !important;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--cd-primary) 20%, transparent) !important;
}

.cd-disk-empty-btn.primary-btn:hover {
  background: var(--cd-primary-gradient-hover) !important;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--cd-primary) 30%, transparent) !important;
  transform: translateY(-1px) !important;
}

.cd-disk-empty-btn.primary-btn:active {
  transform: translateY(0) !important;
}

.cd-disk-empty-btn.default-btn {
  background: color-mix(in srgb, var(--cd-primary) 5%, transparent) !important;
  border: 1px solid color-mix(in srgb, var(--cd-primary) 15%, transparent) !important;
  color: var(--cd-primary) !important;
}

.cd-disk-empty-btn.default-btn:hover {
  background: var(--cd-primary) !important;
  border-color: var(--cd-primary) !important;
  color: #ffffff !important;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--cd-primary) 20%, transparent) !important;
  transform: translateY(-1px) !important;
}

.cd-disk-empty-btn.default-btn:active {
  transform: translateY(0) !important;
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
  border-radius: var(--cd-radius-full);
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
  border-radius: var(--cd-radius-full);
  padding: 3px;
  gap: 2px;
}

.cd-view-btn {
  width: 36px;
  height: 30px;
  border: none;
  background: none;
  border-radius: var(--cd-radius-full);
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

.cd-thumb-wrap {
  position: relative;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
}

.cd-thumb-play {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  font-size: 8px;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 50%;
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
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
