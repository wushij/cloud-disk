<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { fmtSize } from '@/utils/md5'

interface RecycleItem {
  id: number
  name: string
  type: 'file' | 'folder'
  sizeBytes?: number
  deletedAt?: string
}

const items = ref<RecycleItem[]>([])
const loading = ref(false)

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

async function restore(row: RecycleItem) {
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
  await ElMessageBox.confirm('永久删除后不可恢复', '确认', { type: 'warning' })
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
  await ElMessageBox.confirm('清空回收站？', '确认', { type: 'warning' })
  try {
    await http.delete('/api/recycle/clear')
    ElMessage.success('已清空')
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
            <el-icon :size="16" color="var(--cd-warning)"><Delete /></el-icon>
            <span>回收站</span>
            <el-tag v-if="items.length" type="info" size="small" round>{{ items.length }} 项</el-tag>
          </div>
          <el-button type="danger" plain :disabled="!items.length" @click="clearAll">
            <el-icon><Delete /></el-icon>
            清空回收站
          </el-button>
        </div>
      </template>

      <el-empty v-if="!items.length && !loading" description="回收站是空的" />

      <el-table v-else v-loading="loading" :data="items">
        <el-table-column label="名称" min-width="260">
          <template #default="{ row }">
            <div class="cd-file-name">
              <el-icon :size="20" :color="row.type === 'folder' ? 'var(--cd-file-folder)' : 'var(--cd-file-default)'">
                <Folder v-if="row.type === 'folder'" />
                <Document v-else />
              </el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'folder' ? 'warning' : 'info'" size="small" round>
              {{ row.type === 'folder' ? '文件夹' : '文件' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="120">
          <template #default="{ row }">
            <span class="cd-cell-text">{{ row.type === 'file' ? fmtSize(row.sizeBytes || 0) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="删除时间" width="180">
          <template #default="{ row }">
            <span class="cd-cell-text">{{ row.deletedAt ? new Date(row.deletedAt).toLocaleString() : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="restore(row)">
              <el-icon><RefreshLeft /></el-icon>恢复
            </el-button>
            <el-button link type="danger" @click="remove(row)">
              <el-icon><Delete /></el-icon>永久删除
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
  gap: 10px;
  font-weight: 500;
}

.cd-cell-text {
  color: var(--cd-text-secondary);
  font-size: 13px;
}
</style>
