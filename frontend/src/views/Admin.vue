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
const auditPageSize = 8
const rebuilding = ref(false)
const avatarBroken = ref<Record<number, boolean>>({})

const quotaVisible = ref(false)
const quotaSaving = ref(false)
const quotaTarget = ref<UserRow | null>(null)
const quotaInput = ref('')

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
      http.get('/api/admin/audit-logs', { params: { page: 0, size: auditPageSize } })
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

function openQuotaDialog(row: UserRow) {
  quotaTarget.value = row
  const currentGB = (row.storageQuota || 0) / 1024 / 1024 / 1024
  quotaInput.value = String(currentGB)
  quotaVisible.value = true
}

async function submitQuota() {
  if (!quotaTarget.value) return
  const raw = quotaInput.value.trim()
  if (raw === '') {
    ElMessage.warning('请输入存储配额')
    return
  }
  const gb = Number(raw)
  if (Number.isNaN(gb) || gb < 0) {
    ElMessage.warning('请输入有效的配额数值')
    return
  }
  if (quotaSaving.value) return
  quotaSaving.value = true
  const quotaBytes = Math.round(gb * 1024 * 1024 * 1024)
  try {
    await http.put(`/api/admin/users/${quotaTarget.value.id}/quota`, { storageQuota: quotaBytes })
    quotaTarget.value.storageQuota = quotaBytes
    quotaVisible.value = false
    ElMessage.success('配额设置成功')
  } catch {
    /* global toast */
  } finally {
    quotaSaving.value = false
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
          <el-table-column label="操作" width="168" fixed="right">
            <template #default="{ row }">
              <div class="cd-user-actions">
                <button type="button" class="cd-user-action cd-user-action--quota" @click="openQuotaDialog(row)">
                  <el-icon :size="14"><Coin /></el-icon>
                  <span>配额</span>
                </button>
                <button
                  v-if="row.username !== 'admin'"
                  type="button"
                  class="cd-user-action"
                  :class="row.status === 1 ? 'cd-user-action--danger' : 'cd-user-action--primary'"
                  @click="toggleUser(row)"
                >
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="cd-admin-card cd-audit-card">
        <template #header>
          <div class="cd-card-title">
            <el-icon :size="16" color="var(--cd-primary)"><List /></el-icon>
            <span>最近审计日志</span>
            <span class="cd-audit-count">最近 {{ auditLogs.length }} 条</span>
          </div>
        </template>

        <div v-if="!auditLogs.length" class="cd-audit-empty">暂无审计记录</div>

        <el-table
          v-else
          :data="auditLogs"
          class="cd-audit-table"
        >
          <el-table-column label="用户" min-width="108" align="center" header-align="center">
            <template #default="{ row }">
              <span class="cd-audit-user">{{ row.username || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="动作" min-width="96" align="center" header-align="center">
            <template #default="{ row }">
              <span class="cd-audit-action" :class="`tone-${actionTone(row.action)}`">
                {{ actionLabel(row.action) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="详情" min-width="160" align="center" header-align="center" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="cd-audit-detail">{{ row.detail || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="IP" min-width="132" align="center" header-align="center">
            <template #default="{ row }">
              <span class="cd-audit-ip">{{ row.ip || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" min-width="168" align="center" header-align="center">
            <template #default="{ row }">
              <span class="cd-audit-time">{{ fmtTime(row.createTime) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-dialog
      v-model="quotaVisible"
      width="400px"
      destroy-on-close
      class="cd-quota-dialog"
      @closed="quotaTarget = null"
    >
      <template #header>
        <div class="cd-quota-dialog-header">
          <div class="cd-quota-dialog-icon">
            <el-icon :size="20"><Coin /></el-icon>
          </div>
          <div>
            <div class="cd-quota-dialog-title">设置存储配额</div>
            <div v-if="quotaTarget" class="cd-quota-dialog-sub">
              用户 {{ quotaTarget.username }} · 已用 {{ fmtSize(quotaTarget.storageUsed || 0) }}
            </div>
          </div>
        </div>
      </template>

      <p class="cd-quota-hint">输入配额大小，0 表示不限</p>
      <div class="cd-quota-field">
        <el-input
          v-model="quotaInput"
          placeholder="如 10"
          inputmode="decimal"
          @keyup.enter="submitQuota"
        />
        <span class="cd-quota-unit">GB</span>
      </div>

      <template #footer>
        <el-button class="cd-quota-cancel" @click="quotaVisible = false">取消</el-button>
        <el-button type="primary" :loading="quotaSaving" @click="submitQuota">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cd-admin-page {
  padding-top: 16px;
  overflow: auto;
  height: auto;
  min-height: 100%;
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

.cd-audit-card :deep(.el-card__header) {
  background: color-mix(in srgb, var(--theme-bg) 35%, #fff);
}

.cd-audit-empty {
  padding: 40px 16px;
  text-align: center;
  color: var(--cd-text-placeholder);
  font-size: 14px;
}

.cd-audit-table {
  width: 100%;
  --el-table-border-color: var(--cd-border-light);
  --el-table-row-hover-bg-color: color-mix(in srgb, var(--cd-primary) 4%, #fff);
}

.cd-audit-table :deep(.el-table__header th) {
  background: #fff !important;
  font-weight: 600;
  color: var(--cd-text-secondary);
  font-size: 12px;
  padding: 12px 0 !important;
  border-bottom: 1px solid var(--cd-border-light) !important;
}

.cd-audit-table :deep(.el-table__header .cell) {
  text-align: center;
  justify-content: center;
}

.cd-audit-table :deep(.el-table__body td) {
  padding: 12px 8px !important;
  background: #fff !important;
  border-bottom: 1px solid var(--cd-border-light) !important;
}

.cd-audit-table :deep(.el-table__body tr:last-child td) {
  border-bottom: none !important;
}

.cd-audit-table :deep(.el-table__body .cell) {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  line-height: 1.45;
}

.cd-audit-table :deep(.el-table__row) {
  font-size: 13px;
}

.cd-audit-table :deep(.el-table__row:hover > td) {
  background: color-mix(in srgb, var(--cd-primary) 4%, #fff) !important;
}

.cd-audit-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.cd-audit-user {
  font-weight: 600;
  color: var(--cd-text-primary);
}

.cd-audit-action {
  font-size: 13px;
  font-weight: 500;
}

.cd-audit-action.tone-success {
  color: #059669;
}

.cd-audit-action.tone-warning {
  color: #d97706;
}

.cd-audit-action.tone-danger {
  color: #dc2626;
}

.cd-audit-action.tone-info {
  color: var(--cd-text-secondary);
}

.cd-audit-detail {
  color: var(--cd-text-secondary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cd-audit-ip {
  font-family: 'SF Mono', 'Consolas', monospace;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.cd-audit-time {
  color: var(--cd-text-secondary);
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .cd-stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.cd-user-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.cd-user-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: none;
  background: none;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  cursor: pointer;
  transition: color 0.15s ease, opacity 0.15s ease;
}

.cd-user-action--quota,
.cd-user-action--primary {
  color: var(--cd-primary);
}

.cd-user-action--quota:hover,
.cd-user-action--primary:hover {
  color: var(--cd-primary-dark, #4338ca);
}

.cd-user-action--danger {
  color: var(--el-color-danger);
}

.cd-user-action--danger:hover {
  color: #b91c1c;
}

.cd-quota-dialog :deep(.el-dialog__header) {
  padding: 18px 20px 14px !important;
  border-bottom: 1px solid var(--cd-border-light);
}

.cd-quota-dialog :deep(.el-dialog__body) {
  padding: 16px 20px 8px !important;
}

.cd-quota-dialog :deep(.el-dialog__footer) {
  padding: 12px 20px 18px !important;
}

.cd-quota-dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cd-quota-dialog-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--cd-radius);
  background: var(--cd-primary-bg);
  color: var(--cd-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cd-quota-dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text-primary);
  line-height: 1.3;
}

.cd-quota-dialog-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.cd-quota-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--cd-text-secondary);
  line-height: 1.5;
}

.cd-quota-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cd-quota-field :deep(.el-input) {
  flex: 1;
}

.cd-quota-field :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.cd-quota-unit {
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--cd-text-secondary);
}

.cd-quota-cancel {
  min-width: 72px;
}
</style>
