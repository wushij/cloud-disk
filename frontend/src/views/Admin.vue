<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface UserRow {
  id: number
  username: string
  nickname?: string
  role: string
  status: number
  storageQuota?: number
  storageUsed?: number
  createTime?: string
}

const loading = ref(false)
const dashboard = ref<Record<string, unknown>>({})
const users = ref<UserRow[]>([])
const auditLogs = ref<unknown[]>([])
const rebuilding = ref(false)

async function loadAll() {
  loading.value = true
  try {
    const [dash, userList, logs] = await Promise.all([
      http.get('/api/admin/dashboard'),
      http.get('/api/admin/users'),
      http.get('/api/admin/audit-logs', { params: { page: 0, size: 20 } })
    ])
    dashboard.value = dash.data
    users.value = userList.data
    auditLogs.value = logs.data.content || []
  } catch {
    /* global toast */
  } finally {
    loading.value = false
  }
}

async function toggleUser(row: UserRow) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定${action}用户「${row.username}」？`, '确认', { type: 'warning' })
  try {
    await http.put(`/api/admin/users/${row.id}/status`, { status: next })
    row.status = next
    ElMessage.success('操作成功')
  } catch {
    /* global toast */
  }
}

async function rebuildIndex() {
  rebuilding.value = true
  try {
    await http.post('/api/admin/search/rebuild')
    ElMessage.success('索引重建完成')
  } catch {
    /* global toast */
  } finally {
    rebuilding.value = false
  }
}

function fmtSize(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

async function setQuota(row: UserRow) {
  const currentGB = (row.storageQuota || 0) / 1024 / 1024 / 1024
  const { value } = await ElMessageBox.prompt(
    `请输入存储配额（GB），0 表示不限`,
    `设置用户「${row.username}」的配额`,
    { inputValue: String(currentGB), confirmButtonText: '确定', cancelButtonText: '取消' }
  ).catch(() => ({ value: null }))
  if (value === null) return
  const quotaBytes = Math.round(parseFloat(value) * 1024 * 1024 * 1024)
  try {
    await http.put(`/api/admin/users/${row.id}/quota`, { storageQuota: quotaBytes })
    row.storageQuota = quotaBytes
    ElMessage.success('配额设置成功')
  } catch {
    /* global toast */
  }
}

onMounted(loadAll)
</script>

<template>
  <div v-loading="loading" class="cd-page cd-page-scroll cd-admin-page">
    <!-- 统计卡片 -->
    <div class="cd-stat-grid">
      <div class="cd-stat-card">
        <div class="cd-stat-icon cd-stat-blue"><el-icon :size="22"><User /></el-icon></div>
        <div class="cd-stat-info">
          <div class="cd-stat-label">用户数</div>
          <div class="cd-stat-value">{{ dashboard.userCount || 0 }}</div>
        </div>
      </div>
      <div class="cd-stat-card">
        <div class="cd-stat-icon cd-stat-green"><el-icon :size="22"><Document /></el-icon></div>
        <div class="cd-stat-info">
          <div class="cd-stat-label">文件数</div>
          <div class="cd-stat-value">{{ dashboard.fileCount || 0 }}</div>
        </div>
      </div>
      <div class="cd-stat-card">
        <div class="cd-stat-icon cd-stat-purple"><el-icon :size="22"><Coin /></el-icon></div>
        <div class="cd-stat-info">
          <div class="cd-stat-label">存储类型</div>
          <div class="cd-stat-value cd-stat-text">{{ dashboard.storageType || '-' }}</div>
        </div>
      </div>
      <div class="cd-stat-card">
        <div class="cd-stat-icon" :class="dashboard.elasticsearchEnabled ? 'cd-stat-green' : 'cd-stat-gray'">
          <el-icon :size="22"><Search /></el-icon>
        </div>
        <div class="cd-stat-info">
          <div class="cd-stat-label">ES 搜索</div>
          <div class="cd-stat-value cd-stat-text">{{ dashboard.elasticsearchEnabled ? '已启用' : '未启用' }}</div>
        </div>
      </div>
    </div>

    <!-- 用户管理 -->
    <el-card shadow="never" class="cd-admin-card">
      <template #header>
        <div class="cd-card-header">
          <div class="cd-card-title">
            <el-icon :size="16" color="var(--cd-primary)"><UserFilled /></el-icon>
            <span>用户管理</span>
          </div>
          <el-button
            v-if="dashboard.elasticsearchEnabled"
            type="primary"
            :loading="rebuilding"
            @click="rebuildIndex"
          >
            <el-icon><Refresh /></el-icon>
            重建搜索索引
          </el-button>
        </div>
      </template>
      <el-table :data="users">
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="cd-user-cell">
              <el-avatar :size="32" class="cd-user-avatar-sm">
                {{ (row.nickname || row.username || 'U').charAt(0).toUpperCase() }}
              </el-avatar>
              <div>
                <div class="cd-user-nickname">{{ row.nickname || row.username }}</div>
                <div class="cd-user-username">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" size="small" round>{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已用空间" width="110">
          <template #default="{ row }">
            <span class="cd-cell-text">{{ fmtSize(row.storageUsed || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="配额" width="110">
          <template #default="{ row }">
            <span class="cd-cell-text">{{ row.storageQuota ? fmtSize(row.storageQuota) : '不限' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" round>
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="setQuota(row)">
              <el-icon><Coin /></el-icon>配额
            </el-button>
            <el-button
              v-if="row.username !== 'admin'"
              link
              :type="row.status === 1 ? 'danger' : 'primary'"
              @click="toggleUser(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 审计日志 -->
    <el-card shadow="never" class="cd-admin-card">
      <template #header>
        <div class="cd-card-title">
          <el-icon :size="16" color="var(--cd-primary)"><Tickets /></el-icon>
          <span>最近审计日志</span>
        </div>
      </template>
      <el-table :data="auditLogs" size="small">
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column prop="action" label="动作" width="160" />
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="createTime" label="时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
/* 统计卡片 */
.cd-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.cd-stat-card {
  background: var(--cd-bg-white);
  border: 1px solid var(--cd-border-light);
  border-radius: var(--cd-radius-lg);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: var(--cd-transition);
}

.cd-stat-card:hover {
  box-shadow: var(--cd-shadow);
  transform: translateY(-2px);
}

.cd-stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--cd-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.cd-stat-blue { background: linear-gradient(135deg, #4F7CFF, #6366F1); }
.cd-stat-green { background: linear-gradient(135deg, #22C55E, #16A34A); }
.cd-stat-purple { background: linear-gradient(135deg, #A855F7, #7C3AED); }
.cd-stat-gray { background: linear-gradient(135deg, #9CA3AF, #6B7280); }

.cd-stat-info {
  min-width: 0;
}

.cd-stat-label {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-bottom: 4px;
  font-weight: 500;
}

.cd-stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--cd-text-primary);
  line-height: 1.2;
}

.cd-stat-text {
  font-size: 14px;
}

/* 卡片间距 */
.cd-admin-card {
  margin-bottom: 16px;
}

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

/* 用户单元格 */
.cd-user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cd-user-avatar-sm {
  background: var(--cd-primary-gradient) !important;
  color: #fff !important;
  font-weight: 600 !important;
  font-size: 12px !important;
  flex-shrink: 0;
}

.cd-user-nickname {
  font-weight: 500;
  color: var(--cd-text-primary);
  font-size: 14px;
}

.cd-user-username {
  font-size: 12px;
  color: var(--cd-text-placeholder);
}

.cd-cell-text {
  color: var(--cd-text-secondary);
  font-size: 13px;
}

/* 响应式 */
@media (max-width: 768px) {
  .cd-stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
