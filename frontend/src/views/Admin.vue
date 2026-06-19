<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http, { TOKEN_KEY } from '@/api/http'
import { fmtSize, fmtTime } from '@/utils/fileMeta'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

interface UserRow {
  id: number
  username: string
  nickname?: string
  role: string
  status: number
  storageQuota?: number
  storageUsed?: number
  createTime?: string
  hasAvatar?: boolean
}

interface AuditRow {
  id?: number
  username?: string
  action?: string
  detail?: string
  ip?: string
  createTime?: string
}

const loading = ref(false)
const dashboard = ref<Record<string, unknown>>({})
const users = ref<UserRow[]>([])
const auditLogs = ref<AuditRow[]>([])
const rebuilding = ref(false)
const avatarBroken = ref<Record<number, boolean>>({})

const statCards: {
  key: string
  label: string
  icon: string
  tone: string
  text?: boolean
  bool?: boolean
}[] = [
  { key: 'userCount', label: '用户数', icon: 'UserFilled', tone: 'indigo' },
  { key: 'fileCount', label: '文件数', icon: 'Files', tone: 'emerald' },
  { key: 'storageType', label: '存储类型', icon: 'Box', tone: 'violet', text: true },
  { key: 'elasticsearchEnabled', label: 'ES 搜索', icon: 'DataAnalysis', tone: 'slate', bool: true }
]

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

function statValue(card: (typeof statCards)[number]) {
  const raw = dashboard.value[card.key]
  if (card.bool) return raw ? '已启用' : '未启用'
  if (card.text) return String(raw || '-')
  return raw || 0
}

function userAvatarSrc(row: UserRow) {
  if (avatarBroken.value[row.id]) return ''
  if (row.username === auth.username && auth.avatarSrc) return auth.avatarSrc
  if (!row.hasAvatar) return ''
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return ''
  return `/api/admin/users/${row.id}/avatar?access_token=${encodeURIComponent(token)}&v=${auth.avatarVersion}`
}

function onAvatarError(userId: number) {
  avatarBroken.value[userId] = true
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

function actionLabel(action?: string) {
  if (!action) return '操作'
  if (action === 'LOGIN') return '登录'
  if (action.includes('DELETE')) return '删除'
  if (action.includes('UPLOAD')) return '上传'
  if (action.includes('ADMIN')) return '管理'
  return action
}

function actionTone(action?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (!action) return 'info'
  if (action === 'LOGIN') return 'success'
  if (action.includes('DELETE') || action.includes('DISABLE')) return 'danger'
  if (action.includes('ADMIN')) return 'warning'
  return 'info'
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
    <div class="cd-stat-grid">
      <div v-for="card in statCards" :key="card.key" class="cd-stat-card">
        <div class="cd-stat-icon-wrap" :class="`tone-${card.tone}`">
          <el-icon :size="24"><component :is="card.icon" /></el-icon>
        </div>
        <div class="cd-stat-info">
          <div class="cd-stat-label">{{ card.label }}</div>
          <div class="cd-stat-value" :class="{ 'cd-stat-text': card.text || card.bool }">
            {{ statValue(card) }}
          </div>
        </div>
      </div>
    </div>

    <div class="cd-admin-grid">
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
        <el-table :data="users" class="cd-admin-table">
          <el-table-column label="用户" min-width="200">
            <template #default="{ row }">
              <div class="cd-user-cell">
                <el-avatar
                  :size="36"
                  :src="userAvatarSrc(row) || undefined"
                  class="cd-user-avatar-sm"
                  @error="onAvatarError(row.id)"
                >
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

      <el-card shadow="never" class="cd-admin-card cd-audit-card">
        <template #header>
          <div class="cd-card-title">
            <el-icon :size="16" color="var(--cd-primary)"><List /></el-icon>
            <span>最近审计日志</span>
            <span class="cd-audit-count">{{ auditLogs.length }} 条</span>
          </div>
        </template>

        <div v-if="!auditLogs.length" class="cd-audit-empty">暂无审计记录</div>

        <el-table v-else :data="auditLogs" class="cd-audit-table" stripe>
          <el-table-column label="用户" width="120">
            <template #default="{ row }">
              <span class="cd-audit-user">{{ row.username || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="动作" width="100">
            <template #default="{ row }">
              <el-tag :type="actionTone(row.action)" size="small" round effect="light">
                {{ actionLabel(row.action) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="详情" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="cd-audit-detail">{{ row.detail || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="IP" width="140">
            <template #default="{ row }">
              <span class="cd-audit-ip">{{ row.ip || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="180">
            <template #default="{ row }">
              <span class="cd-audit-time">{{ fmtTime(row.createTime) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.cd-admin-page {
  padding-top: 16px;
}

.cd-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}

.cd-stat-card {
  background: var(--cd-bg-white);
  border: 1px solid var(--cd-border-light);
  border-radius: var(--cd-radius-lg);
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: var(--cd-transition);
}

.cd-stat-card:hover {
  box-shadow: var(--cd-shadow);
  border-color: color-mix(in srgb, var(--cd-primary) 16%, var(--cd-border-light));
}

.cd-stat-icon-wrap {
  width: 50px;
  height: 50px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid transparent;
}

.cd-stat-icon-wrap.tone-indigo {
  color: #4f46e5;
  background: linear-gradient(145deg, rgba(79, 70, 229, 0.14), rgba(99, 102, 241, 0.05));
  border-color: rgba(79, 70, 229, 0.12);
}

.cd-stat-icon-wrap.tone-emerald {
  color: #059669;
  background: linear-gradient(145deg, rgba(16, 185, 129, 0.14), rgba(5, 150, 105, 0.05));
  border-color: rgba(16, 185, 129, 0.12);
}

.cd-stat-icon-wrap.tone-violet {
  color: #7c3aed;
  background: linear-gradient(145deg, rgba(124, 58, 237, 0.14), rgba(139, 92, 246, 0.05));
  border-color: rgba(124, 58, 237, 0.12);
}

.cd-stat-icon-wrap.tone-slate {
  color: #64748b;
  background: linear-gradient(145deg, rgba(100, 116, 139, 0.12), rgba(148, 163, 184, 0.05));
  border-color: rgba(100, 116, 139, 0.1);
}

.cd-stat-label {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-bottom: 2px;
  font-weight: 500;
}

.cd-stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--cd-text-primary);
  line-height: 1.2;
}

.cd-stat-text {
  font-size: 15px;
  font-weight: 600;
}

.cd-admin-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.cd-admin-card {
  border-radius: var(--cd-radius-lg) !important;
  border: 1px solid var(--cd-border-light) !important;
}

.cd-admin-card :deep(.el-card__header) {
  padding: 14px 18px !important;
  border-bottom: 1px solid var(--cd-border-light);
}

.cd-admin-card :deep(.el-card__body) {
  padding: 8px 12px 14px !important;
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
  width: 100%;
}

.cd-admin-table :deep(.el-table__header th) {
  background: color-mix(in srgb, var(--theme-bg) 55%, #fff) !important;
  font-weight: 600;
  color: var(--cd-text-secondary);
  font-size: 12px;
}

.cd-user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cd-user-avatar-sm {
  background: var(--cd-primary-gradient) !important;
  color: #fff !important;
  font-weight: 600 !important;
  font-size: 13px !important;
  flex-shrink: 0;
  box-shadow: 0 0 0 2px #fff, 0 0 0 3px var(--theme-primary-muted);
}

.cd-user-nickname {
  font-weight: 600;
  color: var(--cd-text-primary);
  font-size: 14px;
}

.cd-user-username {
  font-size: 12px;
  color: var(--cd-text-placeholder);
  margin-top: 2px;
}

.cd-cell-text {
  color: var(--cd-text-secondary);
  font-size: 13px;
}

.cd-audit-count {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--cd-text-placeholder);
}

.cd-audit-card :deep(.el-card__body) {
  padding: 0 !important;
}

.cd-audit-empty {
  padding: 40px 16px;
  text-align: center;
  color: var(--cd-text-placeholder);
  font-size: 14px;
}

.cd-audit-table {
  width: 100%;
}

.cd-audit-table :deep(.el-table__header th) {
  background: color-mix(in srgb, var(--theme-bg) 55%, #fff) !important;
  font-weight: 600;
  color: var(--cd-text-secondary);
  font-size: 12px;
}

.cd-audit-table :deep(.el-table__row) {
  font-size: 13px;
}

.cd-audit-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.cd-audit-user {
  font-weight: 600;
  color: var(--cd-text-primary);
}

.cd-audit-detail {
  color: var(--cd-text-secondary);
}

.cd-audit-ip {
  font-family: 'SF Mono', 'Consolas', monospace;
  font-size: 12px;
  color: var(--cd-text-secondary);
  padding: 3px 10px;
  background: color-mix(in srgb, var(--theme-bg) 65%, #fff);
  border: 1px solid var(--cd-border-light);
  border-radius: 6px;
}

.cd-audit-time {
  color: var(--cd-text-secondary);
  font-variant-numeric: tabular-nums;
  font-size: 13px;
}

@media (max-width: 768px) {
  .cd-stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
