<script setup lang="ts">
import { ref, onMounted, onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Document, Folder, RefreshLeft, CaretRight } from '@element-plus/icons-vue'
import http from '@/api/http'
import { useConfirmDialogStore } from '@/stores/confirmDialog'
import { fmtSize } from '@/utils/md5'
import PageHeader from '@/components/PageHeader.vue'
import { fileHasCover, fileCoverKind, fileCoverUrl, fileIsVideoCover } from '@/utils/fileCover'
import CachedCover from '@/components/CachedCover.vue'
import FolderTypeIcon from '@/components/FolderTypeIcon.vue'
import { useStorageStore } from '@/stores/storage'

defineOptions({ name: 'Recycle' })

interface RecycleItem {
  id: number
  name: string
  type: 'file' | 'folder'
  sizeBytes?: number
  deletedAt?: string
  mimeType?: string
  hasThumbnail?: boolean
}

const items = ref<RecycleItem[]>([])
const loading = ref(false)
const listInitialized = ref(false)
const confirmDialog = useConfirmDialogStore()

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/api/recycle')
    items.value = data
    listInitialized.value = true
  } catch {
    /* global toast */
  } finally {
    loading.value = false
  }
  void useStorageStore().refresh()
}

function fmtTime(iso?: string) {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

async function restore(row: RecycleItem) {
  const ok = await confirmDialog.open({
    title: '恢复项目',
    message: `确定要恢复「${row.name}」？`,
    confirmText: '恢复',
    danger: false
  })
  if (!ok) return
  try {
    const url =
      row.type === 'folder'
        ? `/api/recycle/restore/folder/${row.id}`
        : `/api/recycle/restore/file/${row.id}`
    await http.post(url)
    ElMessage.success('已恢复')
    load()
  } catch {
    /* global toast */
  }
}

async function remove(row: RecycleItem) {
  const ok = await confirmDialog.open({
    title: '彻底删除',
    message: `确定彻底删除「${row.name}」？此操作无法撤销！`,
    confirmText: '确定',
    danger: true
  })
  if (!ok) return
  try {
    const url =
      row.type === 'folder' ? `/api/recycle/folder/${row.id}` : `/api/recycle/file/${row.id}`
    await http.delete(url)
    ElMessage.success('已永久删除')
    load()
  } catch {
    /* global toast */
  }
}

async function clearAll() {
  const ok = await confirmDialog.open({
    title: '清空回收站',
    message: '确定要清空回收站中的所有项目吗？此操作将永久删除且无法撤销！',
    confirmText: '清空',
    danger: true
  })
  if (!ok) return
  try {
    await http.delete('/api/recycle/clear')
    ElMessage.success('已清空')
    load()
  } catch {
    /* global toast */
  }
}

const isArchive = (row: RecycleItem) => {
  if (row.type !== 'file') return false
  const name = row.name || ''
  const dot = name.lastIndexOf('.')
  const ext = dot > 0 ? name.substring(dot + 1).toLowerCase() : ''
  return ['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)
}

onMounted(() => {
  if (!listInitialized.value) load()
})
</script>

<template>
  <div class="cd-page recycle-page">
    <el-card shadow="never" class="cd-page-card recycle-card">
      <PageHeader
        title="回收站"
        description="已删除的文件与文件夹可在此恢复，或彻底删除释放空间"
        :icon="Delete"
        :count="items.length"
        count-label="项"
      >
        <template #actions>
          <el-button
            v-if="items.length"
            class="recycle-clear-btn"
            plain
            @click="clearAll"
          >
            <el-icon><Delete /></el-icon>
            清空回收站
          </el-button>
        </template>
      </PageHeader>

      <div v-loading="loading" class="recycle-body">
        <div v-if="!loading && !items.length" class="recycle-empty">
          <div class="recycle-empty-icon-wrapper">
            <div class="recycle-empty-icon-bg" />
            <div class="recycle-empty-icon">
              <el-icon :size="32"><Delete /></el-icon>
            </div>
          </div>
          <h3 class="recycle-empty-title">回收站是空的</h3>
          <p class="recycle-empty-desc">已删除的文件可暂存在这里，可随时找回</p>
        </div>

        <el-table v-else :data="items" class="recycle-table" stripe>
          <el-table-column label="名称" min-width="240">
            <template #default="{ row }">
              <div class="recycle-name-cell">
                <div class="recycle-thumb" :class="[row.type, { cover: fileHasCover(row as any) }]">
                  <template v-if="fileHasCover(row as any)">
                    <div v-if="fileCoverKind(row as any) === 'image'" class="recycle-cover-wrap">
                      <CachedCover
                        :file-id="row.id"
                        :src="fileCoverUrl(row as any)"
                        :has-thumbnail="row.hasThumbnail"
                        img-class="recycle-cover"
                      />
                      <div v-if="fileIsVideoCover(row as any)" class="recycle-play-badge">
                        <el-icon><CaretRight /></el-icon>
                      </div>
                    </div>
                  </template>
                  <template v-else>
                    <FolderTypeIcon v-if="row.type === 'folder'" :size="24" />
                    <FolderTypeIcon v-else-if="isArchive(row)" :archive="true" :size="24" />
                    <el-icon v-else :size="20">
                      <Document />
                    </el-icon>
                  </template>
                </div>
                <div class="recycle-name-meta">
                  <span class="recycle-name">{{ row.name }}</span>
                  <span class="recycle-sub">
                    {{ row.type === 'folder' ? '文件夹' : fmtSize(row.sizeBytes || 0) }}
                  </span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="96">
            <template #default="{ row }">
              <span class="recycle-type-tag" :class="row.type">
                {{ row.type === 'folder' ? '文件夹' : '文件' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="删除时间" min-width="168">
            <template #default="{ row }">
              <span class="recycle-time">{{ fmtTime(row.deletedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right" align="right">
            <template #default="{ row }">
              <div class="recycle-actions">
                <button type="button" class="recycle-action restore" @click="restore(row)">
                  <el-icon :size="14"><RefreshLeft /></el-icon>
                  恢复
                </button>
                <button type="button" class="recycle-action purge" @click="remove(row)">
                  <el-icon :size="14"><Delete /></el-icon>
                  删除
                </button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.recycle-page :deep(.cd-page-header-icon) {
  background: var(--cd-primary-gradient) !important;
  color: #ffffff !important;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--cd-primary) 25%, transparent), inset 0 1px 0 rgba(255, 255, 255, 0.25) !important;
}

.recycle-card {
  border: 1px solid var(--cd-border-light) !important;
  border-radius: var(--cd-radius-lg) !important;
  overflow: hidden;
}

.recycle-body {
  min-height: 320px;
}

.recycle-clear-btn {
  border-radius: var(--cd-radius-full) !important;
  background: rgba(239, 68, 68, 0.06) !important;
  border: 1px solid rgba(239, 68, 68, 0.2) !important;
  color: #ef4444 !important;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1) !important;
  font-weight: 600 !important;
}

.recycle-clear-btn:hover {
  background: #ef4444 !important;
  border-color: #ef4444 !important;
  color: #ffffff !important;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.2) !important;
  transform: translateY(-1px) !important;
}

.recycle-clear-btn:active {
  transform: translateY(0) !important;
}

/* ---- 空状态美化 ---- */
.recycle-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
  text-align: center;
  position: relative;
  background: radial-gradient(circle at center, color-mix(in srgb, var(--cd-primary) 4%, transparent) 0%, transparent 70%);
}

.recycle-empty-icon-wrapper {
  position: relative;
  width: 100px;
  height: 100px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.recycle-empty-icon-bg {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, var(--cd-primary) 15%, transparent) 0%, transparent 75%);
  animation: pulseGlow 3s ease-in-out infinite;
}

@keyframes pulseGlow {
  0%, 100% { transform: scale(0.9); opacity: 0.7; }
  50% { transform: scale(1.1); opacity: 1; }
}

.recycle-empty-icon {
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

.recycle-empty-icon:hover {
  transform: translateY(-4px) scale(1.05);
  background: linear-gradient(135deg, color-mix(in srgb, var(--cd-primary) 12%, transparent) 0%, color-mix(in srgb, var(--cd-primary) 18%, transparent) 100%);
  box-shadow: 
    0 12px 30px color-mix(in srgb, var(--cd-primary) 12%, transparent), 
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.recycle-empty-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: 0.5px;
}

.recycle-empty-desc {
  margin: 8px 0 0;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  letter-spacing: 0.2px;
}

/* ---- 表格及行样式 ---- */
.recycle-table :deep(.el-table__header th) {
  background: color-mix(in srgb, var(--theme-bg) 55%, #fff) !important;
  font-weight: 600;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.recycle-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.recycle-table :deep(.el-table__row) {
  transition: all 0.25s ease !important;
}

.recycle-table :deep(.el-table__row:hover > td) {
  background-color: color-mix(in srgb, var(--cd-primary) 3.5%, #ffffff) !important;
}

.recycle-name-cell {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.recycle-thumb {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: rgba(79, 70, 229, 0.08);
  color: var(--cd-primary);
  box-shadow: inset 0 0 0 1px rgba(79, 70, 229, 0.05);
  transition: all 0.2s ease;
}

.recycle-thumb.folder {
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
  box-shadow: inset 0 0 0 1px rgba(245, 158, 11, 0.05);
}

.recycle-name-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.recycle-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--cd-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recycle-sub {
  font-size: 12px;
  color: var(--cd-text-secondary);
  opacity: 0.75;
}

.recycle-type-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 10px;
  border-radius: var(--cd-radius-full);
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  background: rgba(79, 70, 229, 0.06);
  color: var(--cd-primary);
  border: 1px solid rgba(79, 70, 229, 0.1);
}

.recycle-type-tag.folder {
  background: rgba(245, 158, 11, 0.08);
  color: #d97706;
  border-color: rgba(245, 158, 11, 0.15);
}

.recycle-time {
  font-size: 13px;
  color: var(--cd-text-secondary);
  font-variant-numeric: tabular-nums;
}

/* ---- 操作动作 ---- */
.recycle-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.recycle-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--cd-radius-full);
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  background: transparent;
  line-height: 1.2;
}

.recycle-action.restore {
  color: var(--cd-primary);
  background: color-mix(in srgb, var(--cd-primary) 8%, #ffffff);
  border: 1px solid color-mix(in srgb, var(--cd-primary) 15%, transparent);
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.04);
}

.recycle-action.restore:hover {
  background: var(--cd-primary);
  color: #ffffff;
  border-color: var(--cd-primary);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.2);
  transform: translateY(-1px);
}

.recycle-action.restore:active {
  transform: translateY(0);
}

.recycle-action.purge {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.05);
  border: 1px solid rgba(239, 68, 68, 0.12);
  box-shadow: 0 2px 6px rgba(239, 68, 68, 0.02);
}

.recycle-action.purge:hover {
  background: #ef4444;
  color: #ffffff;
  border-color: #ef4444;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.2);
  transform: translateY(-1px);
}

.recycle-action.purge:active {
  transform: translateY(0);
}

.recycle-thumb.cover {
  background: transparent;
  border: 1px solid var(--cd-border-light);
  overflow: hidden;
}

.recycle-cover-wrap {
  position: relative;
  width: 100%;
  height: 100%;
}

.recycle-cover-wrap .recycle-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.recycle-video-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.recycle-video-wrap video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recycle-play-badge {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.25);
  color: #fff;
  font-size: 16px;
}
</style>
