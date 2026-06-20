<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Share, CopyDocument, Close, Document, Folder, Picture, VideoPlay, Headset, Notebook, Files } from '@element-plus/icons-vue'
import http, { TOKEN_KEY } from '@/api/http'
import PageHeader from '@/components/PageHeader.vue'

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
  const token = encodeURIComponent(localStorage.getItem(TOKEN_KEY) || '')
  return `/api/files/${row.fileId}/preview?access_token=${token}`
}

const rows = ref<ShareRow[]>([])
const loading = ref(false)

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
  const url = `${window.location.origin}${row.shareUrl}`
  navigator.clipboard.writeText(url).then(() => ElMessage.success('链接已复制'))
}

async function cancel(row: ShareRow) {
  await ElMessageBox.confirm('确定取消该分享？', '提示', { type: 'warning' })
  try {
    await http.delete(`/api/share/${row.id}`)
    ElMessage.success('已取消')
    load()
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
          <el-table-column label="文件" min-width="220">
            <template #default="{ row }">
              <div class="cd-file-name">
                <div v-if="isImageShare(row)" class="cd-share-cover-wrapper">
                  <img :src="getShareImageUrl(row)" class="cd-share-cover" alt="" />
                </div>
                <el-icon v-else :size="20" :style="{ color: getIconColor(row.fileName) }">
                  <component :is="getFileIcon(row.fileName)" />
                </el-icon>
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
              <div class="cd-action-buttons">
                <template v-if="isExpired(row)">
                  <el-button class="cd-action-btn delete" size="small" @click="cancel(row)">
                    <el-icon><Close /></el-icon>彻底清除
                  </el-button>
                </template>
                <template v-else>
                  <el-button class="cd-action-btn copy" size="small" @click="copyLink(row)">
                    <el-icon><CopyDocument /></el-icon>复制链接
                  </el-button>
                  <el-button class="cd-action-btn cancel" size="small" @click="cancel(row)">
                    <el-icon><Close /></el-icon>取消分享
                  </el-button>
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
.cd-shares-table :deep(.el-table__row) {
  transition: background-color var(--cd-transition-fast), opacity var(--cd-transition-fast);
}

.cd-shares-table :deep(.el-table__row:hover) {
  background-color: var(--cd-bg-surface) !important;
}

.cd-shares-table :deep(.el-table__row.is-expired-row) {
  opacity: 0.6;
  background-color: #f8fafc !important;
}

.cd-shares-table :deep(.el-table__row.is-expired-row .cd-share-filename-text) {
  text-decoration: line-through;
  color: var(--cd-text-placeholder) !important;
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

/* 彻底清除按钮 */
.cd-action-btn.delete {
  background: #fef2f2 !important;
  color: #b91c1c !important;
}

.cd-action-btn.delete:hover {
  background: #fee2e2 !important;
  border-color: #fca5a5 !important;
  transform: translateY(-1px);
}

.cd-share-code {
  font-family: monospace;
  padding: 3px 8px;
  background: var(--cd-border-light);
  border: 1px solid var(--cd-border);
  border-radius: var(--cd-radius-xs);
  color: var(--cd-text-regular);
  font-size: 13px;
  font-weight: 600;
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

.cd-action-buttons {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.cd-action-btn {
  border: 1px solid transparent !important;
  font-weight: 600 !important;
  transition: all var(--cd-transition-fast, 0.15s) !important;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px !important;
  height: 28px !important;
  border-radius: 6px !important;
}

/* 复制链接 */
.cd-action-btn.copy {
  background: #eff6ff !important;
  color: #1d4ed8 !important;
}

.cd-action-btn.copy:hover {
  background: #dbeafe !important;
  border-color: #93c5fd !important;
  transform: translateY(-1px);
}

/* 取消分享 */
.cd-action-btn.cancel {
  background: #fef2f2 !important;
  color: #b91c1c !important;
}

.cd-action-btn.cancel:hover {
  background: #fee2e2 !important;
  border-color: #fca5a5 !important;
  transform: translateY(-1px);
}

.cd-action-btn:active {
  transform: translateY(0) !important;
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
</style>
