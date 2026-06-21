<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Setting,
  Refresh,
  UserFilled,
  Files,
  Coin,
  DataAnalysis,
  Box,
  Clock,
  List,
  ArrowRight
} from '@element-plus/icons-vue'
import http, { TOKEN_KEY } from '@/api/http'
import { fmtSize, fmtTime } from '@/utils/fileMeta'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/PageHeader.vue'

interface AuditRow {
  id?: number
  username?: string
  action?: string
  detail?: string
  ip?: string
  createTime?: string
}

interface AdminUserRow {
  id: number
  username: string
  nickname?: string
  hasAvatar?: boolean
}

interface UserStorageStat {
  userId: number
  username: string
  nickname?: string
  hasAvatar?: boolean
  storageUsed: number
  storageQuota: number
}

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const rebuilding = ref(false)
const dashboard = ref<Record<string, unknown>>({})
const auditLogs = ref<AuditRow[]>([])
const userStats = ref<UserStorageStat[]>([])
const adminUsers = ref<AdminUserRow[]>([])
const avatarBroken = ref<Record<number, boolean>>({})
const auditPageSize = 10

const primaryStats = [
  { key: 'userCount', label: '注册用户', icon: UserFilled, tone: 'indigo' },
  { key: 'pendingUserCount', label: '待审核', icon: Clock, tone: 'amber' },
  { key: 'fileCount', label: '活跃文件', icon: Files, tone: 'emerald' },
  { key: 'totalUsedBytes', label: '存储用量', icon: Coin, tone: 'sky', size: true }
] as const

const infraTags = computed(() => [
  {
    label: '对象存储',
    value: dashboard.value.bucket
      ? `${dashboard.value.storageType} · ${dashboard.value.bucket}`
      : String(dashboard.value.storageType || '-'),
    on: true
  },
  {
    label: 'Redis',
    value: dashboard.value.redisEnabled ? '已启用' : '未启用',
    on: !!dashboard.value.redisEnabled
  },
  {
    label: 'ElasticSearch',
    value: dashboard.value.elasticsearchEnabled ? '已启用' : '未启用',
    on: !!dashboard.value.elasticsearchEnabled
  },
  {
    label: 'RabbitMQ',
    value: dashboard.value.rabbitmqEnabled ? '已启用' : '未启用',
    on: !!dashboard.value.rabbitmqEnabled
  }
])

const quickLinks = computed(() => [
  {
    title: '用户管理',
    icon: UserFilled,
    onClick: () => router.push('/admin/users')
  },
  {
    title: '注册审核',
    icon: Clock,
    badge: Number(dashboard.value.pendingUserCount || 0),
    onClick: () => router.push('/admin/users')
  },
  {
    title: '重建搜索索引',
    icon: DataAnalysis,
    disabled: !dashboard.value.elasticsearchEnabled,
    loading: rebuilding.value,
    onClick: rebuildIndex
  }
])

const topStorageUsers = computed(() =>
  [...userStats.value]
    .filter((u) => (u.storageUsed || 0) > 0)
    .sort((a, b) => (b.storageUsed || 0) - (a.storageUsed || 0))
    .slice(0, 5)
)

const maxStorageUsed = computed(() => topStorageUsers.value[0]?.storageUsed || 1)
const totalUsedBytes = computed(() => Number(dashboard.value.totalUsedBytes || 0))

async function loadAll() {
  loading.value = true
  avatarBroken.value = {}

  try {
    const { data } = await http.get('/api/admin/dashboard')
    dashboard.value = data || {}
  } catch {
    /* global toast */
  }

  try {
    const { data } = await http.get('/api/admin/audit-logs', { params: { page: 0, size: auditPageSize } })
    auditLogs.value = data.content || []
  } catch {
    /* global toast */
  }

  try {
    const { data } = await http.get<AdminUserRow[]>('/api/admin/users')
    adminUsers.value = data || []
  } catch {
    adminUsers.value = []
  }

  try {
    const { data } = await http.get('/api/admin/storage/stats')
    const userMap = new Map(adminUsers.value.map((u) => [u.id, u]))
    userStats.value = (data.userStats || []).map((stat: UserStorageStat) => {
      const profile = userMap.get(stat.userId)
      return {
        ...stat,
        nickname: stat.nickname || profile?.nickname,
        hasAvatar: Boolean(stat.hasAvatar ?? profile?.hasAvatar)
      }
    })
  } catch {
    userStats.value = []
  } finally {
    loading.value = false
  }
}

function statValue(card: (typeof primaryStats)[number]) {
  const raw = dashboard.value[card.key]
  if ('size' in card && card.size) return fmtSize(Number(raw || 0))
  return raw ?? 0
}

async function rebuildIndex() {
  if (!dashboard.value.elasticsearchEnabled || rebuilding.value) return
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

function actionLabel(row: AuditRow) {
  const action = row.action
  if (!action) return '操作'
  if (action === 'LOGIN') return '登录'
  if (action.includes('APPROVE_REGISTER')) return '通过注册'
  if (action.includes('REJECT_REGISTER')) return '拒绝注册'
  if (action.includes('DELETE')) return '删除'
  if (action.includes('UPLOAD')) return '上传'
  if (action.includes('DISABLE') || action.includes('ENABLE')) {
    const detail = (row.detail || '').trim()
    if (detail === '启用' || detail === '禁用') return detail
    return action.includes('DISABLE') ? '禁用' : '启用'
  }
  if (action.includes('ADMIN')) return '管理'
  return action
}

function actionTone(row: AuditRow): 'success' | 'warning' | 'danger' | 'info' {
  const label = actionLabel(row)
  if (label === '登录' || label === '通过注册' || label === '启用') return 'success'
  if (label === '禁用' || label === '拒绝注册' || (row.action || '').includes('DELETE')) return 'danger'
  if ((row.action || '').includes('ADMIN')) return 'warning'
  return 'info'
}

function userDisplayName(user: UserStorageStat) {
  return user.nickname || user.username
}

function userInitial(user: UserStorageStat) {
  return userDisplayName(user).charAt(0).toUpperCase()
}

function userAvatarSrc(user: UserStorageStat) {
  if (!user.userId || avatarBroken.value[user.userId]) return ''
  if (user.username === auth.username && auth.avatarSrc) return auth.avatarSrc
  if (!user.hasAvatar) return ''
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return ''
  const base = import.meta.env.VITE_API_BASE || ''
  return `${base}/api/admin/users/${user.userId}/avatar?access_token=${encodeURIComponent(token)}&v=${auth.avatarVersion}`
}

function onAvatarError(userId: number) {
  avatarBroken.value[userId] = true
}

function storageUsagePercent(user: UserStorageStat) {
  const used = user.storageUsed || 0
  const quota = user.storageQuota || 0
  if (quota > 0) {
    return Math.min(100, Math.max(4, Math.round((used / quota) * 100)))
  }
  return Math.max(6, Math.round((used / maxStorageUsed.value) * 100))
}

onMounted(loadAll)
</script>

<template>
  <div v-loading="loading" class="cd-page cd-page-scroll admin-page">
    <el-card shadow="never" class="admin-shell cd-page-card">
      <template #header>
        <PageHeader title="系统管理" :icon="Setting">
          <template #actions>
            <button type="button" class="admin-refresh" @click="loadAll">
              <el-icon><Refresh /></el-icon>
              刷新数据
            </button>
          </template>
        </PageHeader>
      </template>

      <div class="stats-row">
        <div
          v-for="card in primaryStats"
          :key="card.key"
          class="stat-item"
          :class="[
            `tone-${card.tone}`,
            { 'is-alert': card.key === 'pendingUserCount' && Number(dashboard.pendingUserCount || 0) > 0 }
          ]"
        >
          <div class="stat-icon">
            <el-icon :size="20"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-meta">
            <span class="stat-label">{{ card.label }}</span>
            <strong class="stat-value">{{ statValue(card) }}</strong>
          </div>
        </div>
      </div>

      <section class="ops-section">
        <div class="ops-line">
          <span class="ops-label">基础设施</span>
          <div class="infra-tags">
            <span v-for="tag in infraTags" :key="tag.label" class="infra-tag" :class="{ on: tag.on }">
              <i class="infra-dot" />
              {{ tag.label }} · {{ tag.value }}
            </span>
          </div>
        </div>
        <div class="ops-line ops-line--actions">
          <span class="ops-label">管理入口</span>
          <div class="action-grid">
            <button
              v-for="(link, idx) in quickLinks"
              :key="idx"
              type="button"
              class="action-item"
              :disabled="link.disabled || link.loading"
              @click="link.onClick()"
            >
              <span class="action-icon">
                <el-icon :size="16"><component :is="link.icon" /></el-icon>
              </span>
              <span class="action-title">{{ link.loading ? '处理中…' : link.title }}</span>
              <span v-if="link.badge" class="action-badge">{{ link.badge }}</span>
              <el-icon class="action-arrow"><ArrowRight /></el-icon>
            </button>
          </div>
        </div>
      </section>

      <section class="panel rank-panel">
        <div class="panel-head">
          <div class="panel-title">
            <el-icon :size="16" color="var(--cd-primary)"><Box /></el-icon>
            <span>存储用量 TOP 5</span>
          </div>
          <span class="panel-sub">全平台已用 {{ fmtSize(totalUsedBytes) }}</span>
        </div>
        <div v-if="!topStorageUsers.length" class="panel-empty">暂无用户存储数据</div>
        <ul v-else class="rank-list">
          <li
            v-for="(user, i) in topStorageUsers"
            :key="user.userId"
            class="rank-row"
            :class="`rank-${i + 1}`"
          >
            <span class="rank-no">{{ i + 1 }}</span>
            <el-avatar
              :key="user.userId"
              :size="40"
              :src="userAvatarSrc(user) || undefined"
              class="rank-avatar"
              @error="onAvatarError(user.userId)"
            >
              {{ userInitial(user) }}
            </el-avatar>
            <div class="rank-main">
              <div class="rank-head">
                <div class="rank-user">
                  <span class="rank-name">{{ userDisplayName(user) }}</span>
                  <span class="rank-username">@{{ user.username }}</span>
                </div>
                <strong class="rank-size">{{ fmtSize(user.storageUsed || 0) }}</strong>
              </div>
              <div class="rank-bar">
                <i :style="{ width: `${storageUsagePercent(user)}%` }" />
              </div>
              <div class="rank-foot">
                <span v-if="user.storageQuota">配额 {{ fmtSize(user.storageQuota) }}</span>
                <span v-else>配额不限</span>
                <span v-if="user.storageQuota">已用 {{ storageUsagePercent(user) }}%</span>
              </div>
            </div>
          </li>
        </ul>
      </section>

      <section class="panel audit-panel">
        <div class="panel-head">
          <div class="panel-title">
            <el-icon :size="16" color="var(--cd-primary)"><List /></el-icon>
            <span>最近审计日志</span>
          </div>
          <span class="panel-sub">最近 {{ auditLogs.length }} 条</span>
        </div>
        <div v-if="!auditLogs.length" class="panel-empty">暂无审计记录</div>
        <div v-else class="cd-page-table-wrap">
          <el-table :data="auditLogs" class="audit-table">
            <el-table-column label="用户" min-width="100" align="center">
              <template #default="{ row }">
                <span class="audit-user">{{ row.username || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="动作" min-width="96" align="center">
              <template #default="{ row }">
                <span class="audit-tag" :class="`tone-${actionTone(row)}`">
                  {{ actionLabel(row) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="详情" min-width="160" show-overflow-tooltip align="center">
              <template #default="{ row }">
                <span class="audit-detail">{{ row.detail || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="IP" min-width="128" align="center">
              <template #default="{ row }">
                <span class="audit-mono">{{ row.ip || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="168" align="center">
              <template #default="{ row }">
                <span class="audit-time">{{ fmtTime(row.createTime) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </el-card>
  </div>
</template>

<style scoped>
.admin-page {
  padding-top: 16px;
  min-height: 100%;
}

.admin-shell {
  border-radius: var(--cd-radius-lg) !important;
  border: 1px solid var(--cd-border-light) !important;
  overflow: hidden;
}

.admin-shell :deep(.el-card__header) {
  padding: 0 !important;
  border-bottom: 1px solid var(--cd-border-light) !important;
}

.admin-shell :deep(.el-card__body) {
  padding: 0 22px 22px !important;
}

.admin-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 16px;
  border-radius: 999px;
  border: none;
  background: var(--cd-primary-gradient);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba(1, 7, 16, 0.16);
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.admin-refresh:hover {
  opacity: 0.92;
  transform: translateY(-1px);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin: 18px 0 14px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid var(--cd-border-light);
  background: #fff;
}

.stat-item.is-alert {
  border-color: rgba(245, 158, 11, 0.35);
  background: linear-gradient(135deg, rgba(255, 251, 235, 0.9), #fff);
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-item.tone-indigo .stat-icon { color: #4f46e5; background: rgba(79, 70, 229, 0.1); }
.stat-item.tone-amber .stat-icon { color: #d97706; background: rgba(245, 158, 11, 0.12); }
.stat-item.tone-emerald .stat-icon { color: #059669; background: rgba(16, 185, 129, 0.1); }
.stat-item.tone-sky .stat-icon { color: #0284c7; background: rgba(14, 165, 233, 0.1); }

.stat-label {
  display: block;
  font-size: 12px;
  color: var(--cd-text-secondary);
  font-weight: 500;
}

.stat-value {
  display: block;
  margin-top: 2px;
  font-size: 22px;
  font-weight: 800;
  line-height: 1.15;
  color: var(--cd-text-primary);
  letter-spacing: -0.02em;
}

.ops-section {
  margin-bottom: 14px;
  padding: 14px 16px;
  border: 1px solid var(--cd-border-light);
  border-radius: 14px;
  background: #fff;
}

.ops-line {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.ops-line--actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--cd-border-light);
}

.ops-label {
  width: 56px;
  flex-shrink: 0;
  padding-top: 5px;
  font-size: 12px;
  font-weight: 700;
  color: var(--cd-text-secondary);
  line-height: 1.4;
}

.infra-tags {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.infra-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--cd-text-secondary);
  background: rgba(100, 116, 139, 0.08);
}

.infra-tag.on {
  color: #059669;
  background: rgba(16, 185, 129, 0.1);
}

.infra-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.action-grid {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  height: 44px;
  padding: 0 12px;
  border: 1px solid var(--cd-border-light);
  border-radius: 10px;
  background: color-mix(in srgb, var(--theme-bg) 16%, #fff);
  color: var(--cd-text-primary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-item:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--cd-primary) 22%, var(--cd-border));
  background: #fff;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.05);
}

.action-item:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.action-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--cd-primary);
  background: var(--theme-primary-muted);
}

.action-title {
  flex: 1;
  min-width: 0;
  text-align: left;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #f59e0b;
  color: #fff;
  font-size: 10px;
  font-weight: 800;
  flex-shrink: 0;
}

.action-arrow {
  flex-shrink: 0;
  color: var(--cd-text-placeholder);
  font-size: 12px;
}

.panel {
  border: 1px solid var(--cd-border-light);
  border-radius: 16px;
  background: #fff;
  overflow: hidden;
  margin-bottom: 14px;
}

.audit-panel {
  margin-bottom: 0;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--cd-border-light);
  background: color-mix(in srgb, var(--theme-bg) 25%, #fff);
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: var(--cd-text-primary);
}

.panel-sub {
  font-size: 12px;
  color: var(--cd-text-placeholder);
  font-weight: 500;
}

.panel-empty {
  padding: 32px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--cd-text-placeholder);
}

.rank-list {
  list-style: none;
  margin: 0;
  padding: 8px 0;
}

.rank-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--cd-border-light);
}

.rank-row:last-child {
  border-bottom: none;
}

.rank-no {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--cd-primary) 10%, #fff);
  color: var(--cd-primary);
  font-size: 12px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rank-row.rank-1 .rank-no {
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  color: #b45309;
}

.rank-row.rank-2 .rank-no {
  background: linear-gradient(135deg, #f1f5f9, #e2e8f0);
  color: #475569;
}

.rank-row.rank-3 .rank-no {
  background: linear-gradient(135deg, #ffedd5, #fed7aa);
  color: #c2410c;
}

.rank-avatar {
  flex-shrink: 0;
  font-weight: 700 !important;
  box-shadow: 0 0 0 2px #fff, 0 0 0 3px color-mix(in srgb, var(--cd-primary) 12%, transparent);
}

.rank-avatar:not(:has(img)) {
  background: var(--cd-primary-gradient) !important;
  color: #fff !important;
}

.rank-main {
  flex: 1;
  min-width: 0;
}

.rank-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.rank-user {
  min-width: 0;
}

.rank-name {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: var(--cd-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-username {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--cd-text-placeholder);
}

.rank-size {
  font-size: 14px;
  font-weight: 800;
  color: var(--cd-text-primary);
  white-space: nowrap;
}

.rank-bar {
  height: 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.rank-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--cd-primary-gradient);
}

.rank-foot {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  font-size: 11px;
  color: var(--cd-text-placeholder);
}

.audit-table {
  width: 100%;
}

.audit-user {
  font-weight: 600;
  color: var(--cd-text-primary);
}

.audit-detail {
  color: var(--cd-text-secondary);
}

.audit-tag {
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.audit-tag.tone-success { color: #059669; background: rgba(16, 185, 129, 0.1); }
.audit-tag.tone-warning { color: #d97706; background: rgba(245, 158, 11, 0.12); }
.audit-tag.tone-danger { color: #dc2626; background: rgba(239, 68, 68, 0.1); }
.audit-tag.tone-info { color: var(--cd-text-secondary); background: rgba(100, 116, 139, 0.08); }

.audit-mono {
  font-family: Consolas, monospace;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.audit-time {
  font-size: 13px;
  color: var(--cd-text-secondary);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1100px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .action-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .stats-row {
    grid-template-columns: 1fr;
  }

  .ops-line {
    flex-direction: column;
    gap: 8px;
  }

  .ops-label {
    width: auto;
    padding-top: 0;
  }

  .admin-shell :deep(.el-card__body) {
    padding: 0 14px 14px !important;
  }

  .rank-head {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
