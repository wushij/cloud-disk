<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Document, Folder, RefreshLeft, CaretRight } from '@element-plus/icons-vue'
import http from '@/api/http'
import { useConfirmDialogStore } from '@/stores/confirmDialog'
import { fmtSize } from '@/utils/md5'
import PageHeader from '@/components/PageHeader.vue'
import { fileHasCover, fileCoverKind, fileCoverUrl } from '@/utils/fileCover'
import FolderTypeIcon from '@/components/FolderTypeIcon.vue'

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
const confirmDialog = useConfirmDialogStore()

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/api/recycle')
    items.value = data
  } catch {
    /* global toast */
  } finally {
    loading.value = false
  }
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

onMounted(load)
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
          <div class="recycle-empty-icon">
            <el-icon :size="40"><Delete /></el-icon>
          </div>
          <p class="recycle-empty-title">回收站是空的</p>
          <p class="recycle-empty-desc">删除的文件会暂存在这里，可随时恢复</p>
        </div>

        <el-table v-else :data="items" class="recycle-table" stripe>
          <el-table-column label="名称" min-width="240">
            <template #default="{ row }">
              <div class="recycle-name-cell">
                <div class="recycle-thumb" :class="[row.type, { cover: fileHasCover(row as any) }]">
                  <template v-if="fileHasCover(row as any)">
                    <img
                      v-if="fileCoverKind(row as any) === 'image'"
                      :src="fileCoverUrl(row as any)"
                      class="recycle-cover"
                      alt=""
                    />
                    <div v-else-if="fileCoverKind(row as any) === 'video'" class="recycle-video-wrap">
                      <video
                        :src="fileCoverUrl(row as any)"
                        class="recycle-cover"
                        muted
                      />
                      <div class="recycle-play-badge">
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
  background: linear-gradient(145deg, rgba(245, 158, 11, 0.16), rgba(251, 191, 36, 0.08));
  color: #d97706;
  box-shadow: inset 0 0 0 1px rgba(245, 158, 11, 0.18);
}

.recycle-card {
  border: 1px solid var(--cd-border-light) !important;
  border-radius: var(--cd-radius-lg) !important;
  overflow: hidden;
}

.recycle-body {
  min-height: 280px;
}

.recycle-clear-btn {
  --el-button-text-color: #dc2626;
  --el-button-border-color: rgba(239, 68, 68, 0.35);
  --el-button-hover-text-color: #b91c1c;
  --el-button-hover-border-color: rgba(239, 68, 68, 0.55);
  --el-button-hover-bg-color: rgba(239, 68, 68, 0.06);
}

.recycle-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 72px 24px;
  text-align: center;
}

.recycle-empty-icon {
  width: 88px;
  height: 88px;
  border-radius: 24px;
  background: linear-gradient(145deg, rgba(245, 158, 11, 0.12), rgba(251, 191, 36, 0.05));
  color: #d97706;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.recycle-empty-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text-primary);
}

.recycle-empty-desc {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--cd-text-secondary);
}

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
  transition: background-color 0.15s ease;
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
}

.recycle-thumb.folder {
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
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
  color: var(--cd-text-muted);
}

.recycle-type-tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(79, 70, 229, 0.08);
  color: var(--cd-primary);
}

.recycle-type-tag.folder {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}

.recycle-time {
  font-size: 13px;
  color: var(--cd-text-secondary);
  font-variant-numeric: tabular-nums;
}

.recycle-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.recycle-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 7px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
  background: transparent;
}

.recycle-action.restore {
  color: var(--cd-primary);
  background: var(--theme-primary-muted);
  border-color: color-mix(in srgb, var(--cd-primary) 18%, transparent);
}

.recycle-action.restore:hover {
  background: color-mix(in srgb, var(--cd-primary) 14%, #fff);
}

.recycle-action.purge {
  color: #dc2626;
  background: rgba(239, 68, 68, 0.06);
  border-color: rgba(239, 68, 68, 0.2);
}

.recycle-action.purge:hover {
  background: rgba(239, 68, 68, 0.1);
}

.recycle-thumb.cover {
  background: transparent;
  border: 1px solid var(--cd-border-light);
  overflow: hidden;
}

.recycle-cover {
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
