<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

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
      <template #header>
        <div class="cd-card-header">
          <div class="cd-card-title">
            <el-icon :size="16" color="var(--cd-primary)"><Share /></el-icon>
            <span>我的分享</span>
          </div>
          <el-tag type="info" size="small" round>{{ rows.length }} 条分享</el-tag>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="文件" min-width="220">
          <template #default="{ row }">
            <div class="cd-file-name">
              <el-icon :size="18" color="var(--cd-primary)"><Document /></el-icon>
              <span>{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分享码" width="120">
          <template #default="{ row }">
            <code class="cd-share-code">{{ row.shareCode }}</code>
          </template>
        </el-table-column>
        <el-table-column label="提取码" width="100">
          <template #default="{ row }">
            <code v-if="row.extractCode" class="cd-share-code">{{ row.extractCode }}</code>
            <el-tag v-else size="small" type="success" round>公开</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="浏览/下载" width="110">
          <template #default="{ row }">
            <span class="cd-cell-text">{{ row.viewCount }} / {{ row.downloadCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>
              {{ row.status === 1 ? '有效' : '失效' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="170">
          <template #default="{ row }">
            <span class="cd-cell-text">{{ row.expireTime ? new Date(row.expireTime).toLocaleString() : '永久' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="copyLink(row)">
              <el-icon><CopyDocument /></el-icon>复制链接
            </el-button>
            <el-button v-if="row.status === 1" link type="danger" @click="cancel(row)">
              <el-icon><Close /></el-icon>取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.cd-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cd-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.cd-file-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.cd-share-code {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  padding: 2px 8px;
  background: var(--cd-bg);
  border-radius: var(--cd-radius-sm);
  color: var(--cd-text-primary);
  font-weight: 500;
}

.cd-cell-text {
  color: var(--cd-text-secondary);
  font-size: 13px;
}
</style>
