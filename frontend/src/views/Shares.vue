<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Share, CopyDocument, Close, Document, Folder, Picture, VideoPlay, Headset, Notebook, Files } from '@element-plus/icons-vue'
import http from '@/api/http'
import { buildPublicShareUrl } from '@/utils/shareUrl'
import { mediaTokenParam } from '@/utils/mediaToken'
import { useConfirmDialogStore } from '@/stores/confirmDialog'
import PageHeader from '@/components/PageHeader.vue'
import FolderTypeIcon from '@/components/FolderTypeIcon.vue'

const activeTab = ref<'active' | 'expired'>('active')

function isExpired(row: ShareRow) {
  if (row.status === 0) return true
  if (!row.expireTime) return false
  const exp = new Date(row.expireTime.replace('T', ' ').replace(/-/g, '/'))
  return exp.getTime() < Date.now()
}

const activeList = computed(() => {
  return rows.value.filter(row => !isExpired(row))
})

const expiredList = computed(() => {
  return rows.value.filter(row => isExpired(row))
})

const displayedList = computed(() => {
  return activeTab.value === 'active' ? activeList.value : expiredList.value
})

function tableRowClassName({ row }: { row: ShareRow }) {
  return isExpired(row) ? 'is-expired-row' : ''
}

function getFileIcon(fileName: string) {
  const name = fileName.toLowerCase()
  if (!name.includes('.')) return Folder
  const ext = name.split('.').pop() || ''
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) return Picture
  if (['mp4', 'mkv', 'avi', 'mov', 'flv'].includes(ext)) return VideoPlay
  if (['mp3', 'wav', 'ogg', 'flac'].includes(ext)) return Headset
  if (['pdf', 'doc', 'docx', 'txt', 'md'].includes(ext)) return Notebook
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return Files
  return Document
}

function getIconColor(fileName: string) {
  const name = fileName.toLowerCase()
  if (!name.includes('.')) return '#f59e0b' // Folder - 琥珀
  const ext = name.split('.').pop() || ''
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) return '#10b981' // 图片 - 翡翠
  if (['mp4', 'mkv', 'avi', 'mov', 'flv'].includes(ext)) return '#8b5cf6' // 视频 - 罗兰
  if (['mp3', 'wav', 'ogg', 'flac'].includes(ext)) return '#ec4899' // 音频 - 玫瑰
  if (['pdf', 'doc', 'docx', 'txt', 'md'].includes(ext)) return '#3b82f6' // 文档 - 蓝色
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '#f97316' // 压缩包 - 橙色
  return '#64748b' // 默认 - 灰色
}

interface ShareRow {
  id: number
  shareCode: string
  extractCode: string | null
  fileName: string
  viewCount: number
  downloadCount: number
  status: number
  expireTime: string | null
  shareUrl: string
  createdAt: string
  fileId?: number
  folderId?: number
}

function isImageShare(row: ShareRow) {
  if (!row.fileId) return false
  const name = row.fileName.toLowerCase()
  const ext = name.split('.').pop() || ''
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)
}

function getShareImageUrl(row: ShareRow) {
  const token = mediaTokenParam()
  return `/api/files/${row.fileId}/preview?access_token=${token}`
}

function isFolder(row: ShareRow) {
  if (row.folderId != null) return true
  return !row.fileName.includes('.')
}

function isArchive(row: ShareRow) {
  if (row.folderId != null) return false
  const name = row.fileName.toLowerCase()
  const ext = name.split('.').pop() || ''
  return ['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)
}

const rows = ref<ShareRow[]>([])
const loading = ref(false)
const confirmDialog = useConfirmDialogStore()

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/api/share/mine')
    rows.value = data
  } catch {
    /* global toast */
  } finally {
    loading.value = false
  }
}

function copyLink(row: ShareRow) {
  const url = buildPublicShareUrl(row.shareCode, row.shareUrl)
  navigator.clipboard.writeText(url).then(() => ElMessage.success('链接已复制'))
}

async function cancel(row: ShareRow) {
  const expired = isExpired(row)
  const ok = await confirmDialog.open({
    title: expired ? '彻底删除' : '取消分享',
    message: expired
      ? `确定彻底删除失效分享「${row.fileName}」？删除后无法恢复。`
      : `确定取消分享「${row.fileName}」？`,
    confirmText: expired ? '彻底删除' : '确定',
    danger: true
  })
  if (!ok) return
  try {
    await http.delete(`/api/share/${row.id}`)
    if (expired) {
      rows.value = rows.value.filter((r) => r.id !== row.id)
      ElMessage.success('已彻底删除')
    } else {
      const idx = rows.value.findIndex((r) => r.id === row.id)
      if (idx >= 0) {
        rows.value[idx] = { ...rows.value[idx], status: 0 }
      }
      ElMessage.success('已取消分享')
    }
  } catch {
    /* global toast */
  }
}

onMounted(load)
</script>

<template>
  <div class="cd-page">
    <el-card shadow="never" class="cd-page-card">
      <PageHeader
        title="我的分享"
        description="管理你创建的所有分享链接，支持提取码与过期时间"
        :icon="Share"
        :count="activeList.length"
        count-label="条活跃分享"
      />
      <el-tabs v-model="activeTab" class="cd-shares-tabs">
        <el-tab-pane label="进行中" name="active">
          <template #label>
            <span class="tab-label">
              进行中
              <el-badge :value="activeList.length" :type="activeList.length > 0 ? 'primary' : 'info'" class="tab-badge" />
            </span>
          </template>
        </el-tab-pane>
        <el-tab-pane label="已失效" name="expired">
          <template #label>
            <span class="tab-label">
              已失效
              <el-badge :value="expiredList.length" :type="expiredList.length > 0 ? 'danger' : 'info'" class="tab-badge" />
            </span>
          </template>
        </el-tab-pane>
      </el-tabs>
      <div class="cd-page-table-wrap">
        <el-table 
          v-loading="loading" 
          :data="displayedList" 
          :row-class-name="tableRowClassName"
          class="cd-shares-table"
        >
          <template #empty>
            <div class="shares-empty">
              <div class="shares-empty-icon-wrapper">
                <div class="shares-empty-icon-bg" />
                <div class="shares-empty-icon">
                  <el-icon :size="32"><Share /></el-icon>
                </div>
              </div>
              <h3 class="shares-empty-title">暂无分享链接</h3>
              <p class="shares-empty-desc">在网盘文件列表中，选择文件生成分享链接，在此可以统一管理</p>
            </div>
          </template>
          <el-table-column label="文件" min-width="220">
            <template #default="{ row }">
              <div class="cd-file-name">
                <div v-if="isImageShare(row)" class="cd-share-cover-wrapper">
                  <img :src="getShareImageUrl(row)" class="cd-share-cover" alt="" />
                </div>
                <template v-else>
                  <FolderTypeIcon v-if="isFolder(row)" :size="24" />
                  <FolderTypeIcon v-else-if="isArchive(row)" :archive="true" :size="24" />
                  <el-icon v-else :size="20" :style="{ color: getIconColor(row.fileName) }">
                    <component :is="getFileIcon(row.fileName)" />
                  </el-icon>
                </template>
                <span class="cd-share-filename-text">{{ row.fileName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="分享码" width="120" align="center">
            <template #default="{ row }">
              <code class="cd-share-code">{{ row.shareCode }}</code>
            </template>
          </el-table-column>
          <el-table-column label="提取码" width="100" align="center">
            <template #default="{ row }">
              <code v-if="row.extractCode" class="cd-share-code">{{ row.extractCode }}</code>
              <el-tag v-else size="small" type="success" effect="plain" round>公开</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="浏览/下载" width="110" align="center">
            <template #default="{ row }">
              <span class="cd-cell-text">{{ row.viewCount }} / {{ row.downloadCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="isExpired(row) ? 'info' : 'success'" size="small" round>
                {{ isExpired(row) ? '失效' : '有效' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="过期时间" width="170" align="center">
            <template #default="{ row }">
              <span class="cd-cell-text">{{ row.expireTime ? new Date(row.expireTime).toLocaleString() : '永久' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right" align="center">
            <template #default="{ row }">
              <div class="cd-action-pills">
                <template v-if="isExpired(row)">
                  <button type="button" class="cd-action-pill danger" @click="cancel(row)">
                    <el-icon :size="14"><Close /></el-icon>
                    彻底清除
                  </button>
                </template>
                <template v-else>
                  <button type="button" class="cd-action-pill primary" @click="copyLink(row)">
                    <el-icon :size="14"><CopyDocument /></el-icon>
                    复制链接
                  </button>
                  <button type="button" class="cd-action-pill muted" @click="cancel(row)">
                    <el-icon :size="14"><Close /></el-icon>
                    取消分享
                  </button>
                </template>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.cd-page :deep(.cd-page-header-icon) {
  background: var(--cd-primary-gradient) !important;
  color: #ffffff !important;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--cd-primary) 25%, transparent), inset 0 1px 0 rgba(255, 255, 255, 0.25) !important;
}

.cd-shares-table :deep(.el-table__row) {
  transition: all 0.25s ease !important;
}

.cd-shares-table :deep(.el-table__row:hover > td) {
  background-color: color-mix(in srgb, var(--cd-primary) 3.5%, #ffffff) !important;
}

.cd-shares-table :deep(.el-table__row.is-expired-row) {
  background-color: #f8fafc !important;
}

.cd-shares-table :deep(.el-table__row.is-expired-row .cd-share-filename-text),
.cd-shares-table :deep(.el-table__row.is-expired-row .cd-cell-text),
.cd-shares-table :deep(.el-table__row.is-expired-row .cd-share-code) {
  color: var(--cd-text-placeholder) !important;
  text-decoration: line-through;
}

.cd-shares-table :deep(.el-table__row.is-expired-row .cd-file-name) {
  opacity: 0.72;
}

.cd-shares-table :deep(.el-table__row.is-expired-row .cd-action-pills) {
  opacity: 1;
}

.cd-shares-table :deep(.el-table__inner-wrapper::before) {
  display: none !important;
}

.cd-shares-table :deep(.el-table__border-left-patch) {
  display: none !important;
}

.cd-shares-table :deep(td.el-table__cell) {
  border-bottom: 1px solid #f1f5f9 !important;
}

.cd-shares-table :deep(th.el-table__cell) {
  border-bottom: 1px solid #e2e8f0 !important;
}

.cd-shares-table :deep(.el-tag) {
  border-radius: var(--cd-radius-full);
  font-weight: 600;
  padding: 4px 10px;
  line-height: 1;
  height: auto;
}

.cd-shares-table :deep(.el-tag--success) {
  background-color: rgba(16, 185, 129, 0.06) !important;
  border-color: rgba(16, 185, 129, 0.15) !important;
  color: #10b981 !important;
}

.cd-shares-table :deep(.el-tag--info) {
  background-color: rgba(100, 116, 139, 0.06) !important;
  border-color: rgba(100, 116, 139, 0.15) !important;
  color: #64748b !important;
}

.cd-shares-tabs {
  margin-top: 16px;
  margin-bottom: 4px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
}

.tab-badge :deep(.el-badge__content) {
  top: 0 !important;
  transform: translateY(0) !important;
  font-weight: 700;
}

.cd-share-code {
  font-family: monospace;
  padding: 3px 8px;
  background: color-mix(in srgb, var(--cd-primary) 5%, transparent) !important;
  border: 1px solid color-mix(in srgb, var(--cd-primary) 12%, transparent) !important;
  border-radius: var(--cd-radius-xs);
  color: var(--cd-primary) !important;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.cd-share-filename-text {
  font-weight: 600;
  color: var(--cd-text-primary);
  margin-left: 6px;
}

.cd-cell-text {
  font-size: 13px;
  color: var(--cd-text-secondary);
}

.cd-share-cover-wrapper {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cd-bg-surface);
  border: 1px solid var(--cd-border-light);
  flex-shrink: 0;
  box-shadow: var(--cd-shadow-xs);
}

.cd-share-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ---- 空状态美化 ---- */
.shares-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
  text-align: center;
  position: relative;
  background: radial-gradient(circle at center, color-mix(in srgb, var(--cd-primary) 4%, transparent) 0%, transparent 70%);
}

.shares-empty-icon-wrapper {
  position: relative;
  width: 100px;
  height: 100px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.shares-empty-icon-bg {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, var(--cd-primary) 15%, transparent) 0%, transparent 75%);
  animation: sharePulseGlow 3s ease-in-out infinite;
}

@keyframes sharePulseGlow {
  0%, 100% { transform: scale(0.9); opacity: 0.7; }
  50% { transform: scale(1.1); opacity: 1; }
}

.shares-empty-icon {
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

.shares-empty-icon:hover {
  transform: translateY(-4px) scale(1.05);
  background: linear-gradient(135deg, color-mix(in srgb, var(--cd-primary) 12%, transparent) 0%, color-mix(in srgb, var(--cd-primary) 18%, transparent) 100%);
  box-shadow: 
    0 12px 30px color-mix(in srgb, var(--cd-primary) 12%, transparent), 
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.shares-empty-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: 0.5px;
}

.shares-empty-desc {
  margin: 8px 0 0;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  letter-spacing: 0.2px;
}

/* ---- 操作动作 ---- */
.cd-action-pills {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.cd-action-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--cd-radius-full);
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1) !important;
  background: transparent;
  line-height: 1.2;
}

.cd-action-pill.primary {
  color: var(--cd-primary) !important;
  background: color-mix(in srgb, var(--cd-primary) 8%, #ffffff) !important;
  border: 1px solid color-mix(in srgb, var(--cd-primary) 15%, transparent) !important;
  box-shadow: 0 2px 6px color-mix(in srgb, var(--cd-primary) 4%, transparent) !important;
}

.cd-action-pill.primary:hover {
  background: var(--cd-primary) !important;
  color: #ffffff !important;
  border-color: var(--cd-primary) !important;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--cd-primary) 20%, transparent) !important;
  transform: translateY(-1px) !important;
}

.cd-action-pill.primary:active {
  transform: translateY(0) !important;
}

.cd-action-pill.muted {
  color: #64748b !important;
  background: rgba(100, 116, 139, 0.05) !important;
  border: 1px solid rgba(100, 116, 139, 0.12) !important;
  box-shadow: 0 2px 6px rgba(100, 116, 139, 0.02) !important;
}

.cd-action-pill.muted:hover {
  background: #64748b !important;
  color: #ffffff !important;
  border-color: #64748b !important;
  box-shadow: 0 4px 12px rgba(100, 116, 139, 0.15) !important;
  transform: translateY(-1px) !important;
}

.cd-action-pill.muted:active {
  transform: translateY(0) !important;
}

.cd-action-pill.danger {
  color: #ef4444 !important;
  background: rgba(239, 68, 68, 0.05) !important;
  border: 1px solid rgba(239, 68, 68, 0.12) !important;
  box-shadow: 0 2px 6px rgba(239, 68, 68, 0.02) !important;
}

.cd-action-pill.danger:hover {
  background: #ef4444 !important;
  color: #ffffff !important;
  border-color: #ef4444 !important;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.2) !important;
  transform: translateY(-1px) !important;
}

.cd-action-pill.danger:active {
  transform: translateY(0) !important;
}
</style>
