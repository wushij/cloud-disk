<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled, Coin, Lock, Check, Close, Search } from '@element-plus/icons-vue'
import http from '@/api/http'
import { mediaTokenParam } from '@/utils/mediaToken'
import { fmtSize } from '@/utils/fileMeta'
import { useAuthStore } from '@/stores/auth'
import { useConfirmDialogStore } from '@/stores/confirmDialog'
import PageHeader from '@/components/PageHeader.vue'

const auth = useAuthStore()
const confirmDialog = useConfirmDialogStore()

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
  canManage?: boolean
  canApprove?: boolean
  canAssignRole?: boolean
}

const loading = ref(false)
const users = ref<UserRow[]>([])
const avatarBroken = ref<Record<number, boolean>>({})

// 过滤状态
const searchKeyword = ref('')
const filterRole = ref('')
const filterStatus = ref<number | ''>('')

// 配额弹窗
const quotaVisible = ref(false)
const quotaSaving = ref(false)
const quotaTarget = ref<UserRow | null>(null)
const quotaInput = ref('')

// 重置密码弹窗
const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdTarget = ref<UserRow | null>(null)
const pwdInput = ref('')

async function loadUsers() {
  loading.value = true
  try {
    const { data } = await http.get<UserRow[]>('/api/admin/users')
    users.value = data || []
  } catch {
    /* global toast */
  } finally {
    loading.value = false
  }
}

function roleLabel(role: string) {
  switch (role) {
    case 'SUPER_ADMIN':
      return '超级管理员'
    case 'ADMIN':
      return '管理员'
    default:
      return '普通用户'
  }
}

function roleTagType(role: string) {
  if (role === 'SUPER_ADMIN') return 'danger'
  if (role === 'ADMIN') return 'warning'
  return 'info'
}

function isAdminRole(role: string) {
  return role === 'ADMIN' || role === 'SUPER_ADMIN'
}

const filteredUsers = computed(() => {
  const list = users.value.filter((u) => {
    const keyword = searchKeyword.value.trim().toLowerCase()
    const matchesKeyword =
      !keyword ||
      u.username.toLowerCase().includes(keyword) ||
      (u.nickname && u.nickname.toLowerCase().includes(keyword))

    let matchesRole = true
    if (filterRole.value === 'ADMIN') {
      matchesRole = isAdminRole(u.role)
    } else if (filterRole.value === 'USER') {
      matchesRole = u.role === 'USER'
    }
    const matchesStatus = filterStatus.value === '' || u.status === filterStatus.value

    return matchesKeyword && matchesRole && matchesStatus
  })
  return list.sort((a, b) => {
    const roleOrder = (r: string) => (r === 'SUPER_ADMIN' ? 0 : r === 'ADMIN' ? 1 : 2)
    const aOrder = roleOrder(a.role)
    const bOrder = roleOrder(b.role)
    if (aOrder !== bOrder) return aOrder - bOrder
    return (b.createTime || '').localeCompare(a.createTime || '')
  })
})

// 分页状态
const currentPage = ref(1)
const pageSize = ref(10)

const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredUsers.value.slice(start, end)
})

watch([searchKeyword, filterRole, filterStatus], () => {
  currentPage.value = 1
})

function userAvatarSrc(row: UserRow) {
  if (avatarBroken.value[row.id]) return ''
  if (row.username === auth.username && auth.avatarDisplaySrc) return auth.avatarDisplaySrc
  if (!row.hasAvatar) return ''
  const token = mediaTokenParam()
  if (!token) return ''
  return `/api/admin/users/${row.id}/avatar?access_token=${token}&v=${auth.avatarVersion}`
}

function onAvatarError(userId: number) {
  avatarBroken.value[userId] = true
}

// 启用/禁用用户
async function toggleUserStatus(row: UserRow) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '禁用' : '启用'
  const ok = await confirmDialog.open({
    title: '确认操作',
    message: `确定${action}用户「${row.nickname || row.username}」？${next === 0 ? '禁用后该用户的所有在线会话将被注销！' : ''}`,
    confirmText: '确定',
    danger: next === 0
  })
  if (!ok) return
  try {
    await http.put(`/api/admin/users/${row.id}/status`, { status: next })
    row.status = next
    ElMessage.success('操作成功')
  } catch {
    /* global toast */
  }
}

// 修改角色（仅超级管理员，以后端 canAssignRole 为准）
async function toggleUserRole(row: UserRow) {
  if (!row.canAssignRole) return
  const isCurrentlyAdmin = row.role === 'ADMIN'
  const targetRole = isCurrentlyAdmin ? 'USER' : 'ADMIN'
  const action = isCurrentlyAdmin ? '降为普通用户' : '设为管理员'
  const ok = await confirmDialog.open({
    title: '修改角色',
    message: `确定将用户「${row.nickname || row.username}」${action}吗？`,
    confirmText: '确定',
    danger: isCurrentlyAdmin
  })
  if (!ok) return
  try {
    const { data } = await http.put<{ role?: string; storageQuota?: number }>(
      `/api/admin/users/${row.id}/role`,
      { role: targetRole }
    )
    row.role = data?.role || targetRole
    if (data?.storageQuota != null) {
      row.storageQuota = data.storageQuota
    }
    ElMessage.success('角色修改成功')
  } catch {
    /* global toast */
  }
}

// 审批注册：通过
async function handleApprove(row: UserRow) {
  const ok = await confirmDialog.open({
    title: '通过注册申请',
    message: `确定通过「${row.nickname || row.username}」的注册申请吗？通过后该账号将被激活并分配 3GB 存储空间。`,
    confirmText: '通过',
    danger: false
  })
  if (!ok) return
  try {
    await http.post(`/api/admin/registrations/${row.id}/approve`)
    ElMessage.success('已通过注册申请')
    loadUsers()
  } catch {
    /* global toast */
  }
}

// 审批注册：拒绝
async function handleReject(row: UserRow) {
  const ok = await confirmDialog.open({
    title: '拒绝注册申请',
    message: `确定拒绝「${row.nickname || row.username}」的注册申请吗？该申请记录将被永久清理。`,
    confirmText: '拒绝',
    danger: true
  })
  if (!ok) return
  try {
    await http.post(`/api/admin/registrations/${row.id}/reject`)
    ElMessage.success('已拒绝注册申请')
    loadUsers()
  } catch {
    /* global toast */
  }
}

// 存储配额
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

// 重置密码
function openPwdDialog(row: UserRow) {
  pwdTarget.value = row
  pwdInput.value = ''
  pwdVisible.value = true
}

async function submitResetPwd() {
  if (!pwdTarget.value) return
  const pwd = pwdInput.value.trim()
  if (!pwd) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (pwd.length < 6) {
    ElMessage.warning('密码长度不能小于 6 位')
    return
  }
  pwdSaving.value = true
  try {
    await http.put(`/api/admin/users/${pwdTarget.value.id}/password`, { password: pwd })
    pwdVisible.value = false
    ElMessage.success('密码重置成功，该用户在线会话已失效')
  } catch {
    /* global toast */
  } finally {
    pwdSaving.value = false
  }
}

function fmtTime(iso?: string) {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour12: false })
}

onMounted(async () => {
  await auth.fetchProfile().catch(() => {})
  loadUsers()
})
</script>

<template>
  <div v-loading="loading" class="cd-page cd-page-scroll usermap-page">
    <el-card shadow="never" class="cd-admin-card">
      <template #header>
        <PageHeader
          title="用户管理"
          description="管理网盘所有用户的配额容量、角色权限及账户状态，审核或清除注册申请。"
          :icon="UserFilled"
          :count="filteredUsers.length"
          count-label="个用户"
        />
      </template>

      <!-- 搜索过滤面板 -->
      <div class="user-filter-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索用户名、昵称"
          clearable
          :prefix-icon="Search"
          class="filter-search"
        />
        <el-select v-model="filterRole" placeholder="全部用户" clearable class="filter-select">
          <el-option label="全部用户" value="" />
          <el-option label="管理员" value="ADMIN" />
          <el-option label="普通用户" value="USER" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="全部状态" clearable class="filter-select">
          <el-option label="正常" :value="1" />
          <el-option label="待审核" :value="2" />
          <el-option label="已禁用" :value="0" />
        </el-select>
      </div>

      <el-table :data="pagedUsers" class="cd-admin-table">
        <!-- 用户信息 -->
        <el-table-column label="用户信息" min-width="220" header-align="center">
          <template #default="{ row }">
            <div class="cd-user-cell">
              <el-avatar
                :size="38"
                :src="userAvatarSrc(row) || undefined"
                class="cd-user-avatar-sm"
                @error="onAvatarError(row.id)"
              >
                {{ (row.nickname || row.username || 'U').charAt(0).toUpperCase() }}
              </el-avatar>
              <div class="user-meta-info">
                <div class="cd-user-nickname">{{ row.nickname || row.username }}</div>
                <div class="cd-user-username">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <!-- 角色属性 -->
        <el-table-column label="角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag
              :type="roleTagType(row.role)"
              size="small"
              round
              effect="plain"
              class="role-tag"
            >
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 已用空间 -->
        <el-table-column label="已用存储" width="130" align="center">
          <template #default="{ row }">
            <span class="cd-cell-text">{{ fmtSize(row.storageUsed || 0) }}</span>
          </template>
        </el-table-column>

        <!-- 容量配额 -->
        <el-table-column label="空间配额" width="130" align="center">
          <template #default="{ row }">
            <span class="cd-cell-text quota-text" :class="{ unlimited: !row.storageQuota }">
              {{ row.storageQuota ? fmtSize(row.storageQuota) : '不限' }}
            </span>
          </template>
        </el-table-column>

        <!-- 账户状态 -->
        <el-table-column label="账号状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 1 ? 'success' : row.status === 2 ? 'warning' : 'danger'"
              size="small"
              round
              effect="light"
            >
              {{ row.status === 1 ? '正常' : row.status === 2 ? '待审核' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 创建时间 -->
        <el-table-column label="注册时间" min-width="168" align="center">
          <template #default="{ row }">
            <span class="cd-cell-text time-text">{{ fmtTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <!-- 操作行为 -->
        <el-table-column label="管理操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <div class="cd-admin-actions">
              <!-- 正常状态下的管理行为 -->
              <template v-if="row.status !== 2 && row.canManage">
                <button type="button" class="cd-admin-action quota" title="设置空间配额" @click="openQuotaDialog(row)">
                  <el-icon :size="13"><Coin /></el-icon>
                  配额
                </button>
                <button type="button" class="cd-admin-action pwd" title="重置登录密码" @click="openPwdDialog(row)">
                  <el-icon :size="13"><Lock /></el-icon>
                  密码
                </button>
                <button
                  v-if="row.canAssignRole"
                  type="button"
                  class="cd-admin-action role-toggle"
                  title="切换管理员角色"
                  @click="toggleUserRole(row)"
                >
                  角色
                </button>
                <button
                  type="button"
                  class="cd-admin-action"
                  :class="row.status === 1 ? 'danger' : 'success'"
                  :title="row.status === 1 ? '禁用账户' : '启用账户'"
                  @click="toggleUserStatus(row)"
                >
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </button>
              </template>

              <!-- 待审批状态下的管理行为 -->
              <template v-else-if="row.status === 2 && row.canApprove">
                <button type="button" class="cd-admin-action success approve" title="同意注册申请" @click="handleApprove(row)">
                  <el-icon :size="13"><Check /></el-icon>
                  通过
                </button>
                <button type="button" class="cd-admin-action danger reject" title="拒绝注册申请" @click="handleReject(row)">
                  <el-icon :size="13"><Close /></el-icon>
                  拒绝
                </button>
              </template>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页栏 -->
      <div class="user-pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="filteredUsers.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          class="cd-admin-pagination"
        />
      </div>
    </el-card>

    <!-- 配额调整模态框 -->
    <el-dialog
      v-model="quotaVisible"
      width="420px"
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
            <div class="cd-quota-dialog-title">分配存储配额</div>
            <div v-if="quotaTarget" class="cd-quota-dialog-sub">
              用户 {{ quotaTarget.nickname || quotaTarget.username }} · 已用 {{ fmtSize(quotaTarget.storageUsed || 0) }}
            </div>
          </div>
        </div>
      </template>

      <p class="cd-quota-hint">请输入新的最大可用空间容量。设为 0 表示不做容量限制。</p>
      <div class="cd-quota-field">
        <el-input
          v-model="quotaInput"
          placeholder="例如 50"
          inputmode="decimal"
          @keyup.enter="submitQuota"
        />
        <span class="cd-quota-unit">GB</span>
      </div>

      <template #footer>
        <div class="cd-dialog-footer-pills">
          <el-button size="large" class="cd-quota-cancel" @click="quotaVisible = false">取消</el-button>
          <el-button type="primary" size="large" :loading="quotaSaving" @click="submitQuota">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 密码重置模态框 -->
    <el-dialog
      v-model="pwdVisible"
      width="420px"
      destroy-on-close
      class="cd-quota-dialog cd-pwd-dialog"
      @closed="pwdTarget = null"
    >
      <template #header>
        <div class="cd-quota-dialog-header pwd-header">
          <div class="cd-quota-dialog-icon pwd-icon">
            <el-icon :size="20"><Lock /></el-icon>
          </div>
          <div>
            <div class="cd-quota-dialog-title">重置用户密码</div>
            <div v-if="pwdTarget" class="cd-quota-dialog-sub">
              正在为 @{{ pwdTarget.username }} 的账户设置新密码
            </div>
          </div>
        </div>
      </template>

      <p class="cd-quota-hint">重置后，该用户的所有在线登录会话将<strong>立即强制失效</strong>，需重新输入新密码进行登录。</p>
      <div class="cd-quota-field pwd-field">
        <el-input
          v-model="pwdInput"
          placeholder="请输入至少 6 位的新密码"
          type="password"
          show-password
          @keyup.enter="submitResetPwd"
        />
      </div>

      <template #footer>
        <div class="cd-dialog-footer-pills">
          <el-button size="large" class="cd-quota-cancel" @click="pwdVisible = false">取消</el-button>
          <el-button type="primary" size="large" :loading="pwdSaving" @click="submitResetPwd">确定重置</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.usermap-page {
  padding-top: 16px;
  overflow: auto;
  min-height: 100%;
}

.cd-admin-card {
  border-radius: var(--cd-radius-lg) !important;
  border: 1px solid var(--cd-border-light) !important;
}

.cd-admin-card :deep(.el-card__header) {
  padding: 0 !important;
  border-bottom: none;
}

.cd-admin-card :deep(.el-card__body) {
  padding: 16px 20px 20px !important;
}

/* 过滤栏设计 */
.user-filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.filter-search {
  width: 260px;
}

.filter-search :deep(.el-input__wrapper) {
  border-radius: var(--cd-radius-full);
}

.filter-select {
  width: 180px;
}

.filter-select :deep(.el-select__wrapper) {
  border-radius: var(--cd-radius-full);
}

/* 表格定制 */
.cd-admin-table :deep(.el-table__header th) {
  background: color-mix(in srgb, var(--theme-bg) 55%, #fff) !important;
  font-weight: 600;
  color: var(--cd-text-secondary);
  font-size: 12px;
  padding: 12px 0 !important;
}

.cd-admin-table :deep(.el-table__row td) {
  padding: 12px 0 !important;
}

.cd-admin-table :deep(.el-table__row) {
  font-size: 13.5px;
}

.cd-user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cd-user-avatar-sm {
  background: var(--cd-primary-gradient) !important;
  color: #fff !important;
  font-weight: 700 !important;
  font-size: 13.5px !important;
  flex-shrink: 0;
  box-shadow: 0 0 0 2px #fff, 0 0 0 3px color-mix(in srgb, var(--cd-primary) 15%, transparent);
}

.user-meta-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.cd-user-nickname {
  font-weight: 650;
  color: var(--cd-text-primary);
  font-size: 13.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cd-user-username {
  font-size: 12px;
  color: var(--cd-text-placeholder);
}

.role-tag {
  font-weight: 600;
}

.cd-cell-text {
  color: var(--cd-text-secondary);
  font-weight: 500;
}

.quota-text.unlimited {
  color: var(--cd-text-placeholder);
  font-style: italic;
}

.time-text {
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}

/* 操作区域 */
.cd-admin-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
}

.cd-admin-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: var(--cd-radius-full);
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  background: transparent;
  white-space: nowrap;
}

.cd-admin-action.quota {
  color: var(--cd-primary);
  background: color-mix(in srgb, var(--cd-primary) 8%, #ffffff);
  border-color: color-mix(in srgb, var(--cd-primary) 15%, transparent);
}

.cd-admin-action.quota:hover {
  background: var(--cd-primary);
  border-color: var(--cd-primary);
  color: #ffffff;
  transform: translateY(-1px);
  box-shadow: 0 4px 10px color-mix(in srgb, var(--cd-primary) 20%, transparent);
}

.cd-admin-action.pwd {
  color: #7c3aed;
  background: rgba(124, 58, 237, 0.05);
  border-color: rgba(124, 58, 237, 0.15);
}

.cd-admin-action.pwd:hover {
  background: #7c3aed;
  border-color: #7c3aed;
  color: #ffffff;
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(124, 58, 237, 0.2);
}

.cd-admin-action.role-toggle {
  color: #0ea5e9;
  background: rgba(14, 165, 233, 0.05);
  border-color: rgba(14, 165, 233, 0.15);
}

.cd-admin-action.role-toggle:hover {
  background: #0ea5e9;
  border-color: #0ea5e9;
  color: #ffffff;
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(14, 165, 233, 0.2);
}

.cd-admin-action.danger {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.05);
  border-color: rgba(239, 68, 68, 0.15);
}

.cd-admin-action.danger:hover {
  background: #ef4444;
  border-color: #ef4444;
  color: #ffffff;
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(239, 68, 68, 0.2);
}

.cd-admin-action.success {
  color: #10b981;
  background: rgba(16, 185, 129, 0.05);
  border-color: rgba(16, 185, 129, 0.15);
}

.cd-admin-action.success:hover {
  background: #10b981;
  border-color: #10b981;
  color: #ffffff;
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(16, 185, 129, 0.2);
}

/* 弹窗设计 */
.cd-quota-dialog :deep(.el-dialog__header) {
  padding: 20px 22px 14px !important;
  border-bottom: 1px solid var(--cd-border-light);
}

.cd-quota-dialog :deep(.el-dialog__body) {
  padding: 18px 22px 8px !important;
}

.cd-quota-dialog :deep(.el-dialog__footer) {
  padding: 12px 22px 20px !important;
}

.cd-quota-dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cd-quota-dialog-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--cd-primary) 8%, #ffffff);
  color: var(--cd-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--cd-primary) 12%, transparent);
}

.pwd-icon {
  background: rgba(124, 58, 237, 0.08);
  color: #7c3aed;
  box-shadow: inset 0 0 0 1px rgba(124, 58, 237, 0.15);
}

.cd-quota-dialog-title {
  font-size: 16px;
  font-weight: 750;
  color: var(--cd-text-primary);
  line-height: 1.3;
}

.cd-quota-dialog-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--cd-text-secondary);
  font-weight: 500;
}

.cd-quota-hint {
  margin: 0 0 14px;
  font-size: 13.5px;
  color: var(--cd-text-secondary);
  line-height: 1.5;
}

.cd-quota-hint strong {
  color: var(--cd-text-primary);
  font-weight: 700;
}

.cd-quota-field {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cd-quota-field :deep(.el-input__wrapper) {
  border-radius: 12px;
  padding: 8px 12px;
}

.cd-quota-unit {
  font-size: 14.5px;
  font-weight: 700;
  color: var(--cd-text-secondary);
}

.cd-quota-cancel {
  border-radius: var(--cd-radius-full);
}

.cd-dialog-footer-pills {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.cd-dialog-footer-pills :deep(.el-button) {
  border-radius: var(--cd-radius-full);
}

/* 分页栏样式 */
.user-pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
  padding: 0 4px;
}

.cd-admin-pagination {
  --el-pagination-hover-color: var(--cd-primary);
  display: flex;
  align-items: center;
}

/* 总条数文本 */
.cd-admin-pagination :deep(.el-pagination__total) {
  font-size: 13px;
  color: var(--cd-text-secondary);
  font-weight: 500;
  margin-right: 16px;
}

/* 每页条数下拉框 */
.cd-admin-pagination :deep(.el-pagination__sizes) {
  margin-right: 16px;
}

.cd-admin-pagination :deep(.el-pagination__sizes .el-select__wrapper) {
  border-radius: var(--cd-radius-full) !important;
  box-shadow: 0 0 0 1px var(--cd-border) inset !important;
  background-color: var(--cd-bg-white) !important;
  padding: 4px 12px !important;
  height: 32px !important;
  transition: var(--cd-transition-fast);
}

.cd-admin-pagination :deep(.el-pagination__sizes .el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--cd-primary-light) inset !important;
}

.cd-admin-pagination :deep(.el-pagination__sizes .el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--cd-primary) inset, 0 0 0 3px var(--theme-primary-muted) !important;
}

/* 翻页按钮（左/右） */
.cd-admin-pagination :deep(.btn-prev),
.cd-admin-pagination :deep(.btn-next) {
  background-color: var(--cd-bg-white) !important;
  border: 1px solid var(--cd-border) !important;
  border-radius: 50% !important;
  width: 32px !important;
  height: 32px !important;
  min-width: 32px !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  color: var(--cd-text-secondary) !important;
  transition: var(--cd-transition-fast) !important;
  margin: 0 4px !important;
  padding: 0 !important;
}

.cd-admin-pagination :deep(.btn-prev:not(:disabled):hover),
.cd-admin-pagination :deep(.btn-next:not(:disabled):hover) {
  border-color: var(--cd-primary) !important;
  color: var(--cd-primary) !important;
  background-color: var(--theme-primary-muted) !important;
  transform: translateY(-1px) scale(1.05);
}

.cd-admin-pagination :deep(.btn-prev:disabled),
.cd-admin-pagination :deep(.btn-next:disabled) {
  background-color: var(--cd-bg) !important;
  border-color: var(--cd-border-light) !important;
  color: var(--cd-text-placeholder) !important;
  opacity: 0.6;
  cursor: not-allowed;
}

/* 页码按钮 */
.cd-admin-pagination :deep(.el-pager li) {
  background: transparent !important;
  border: 1px solid transparent !important;
  border-radius: 50% !important;
  width: 32px !important;
  height: 32px !important;
  min-width: 32px !important;
  line-height: 30px !important;
  color: var(--cd-text-secondary) !important;
  font-weight: 600 !important;
  font-size: 13px !important;
  margin: 0 3px !important;
  transition: var(--cd-transition-fast) !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.cd-admin-pagination :deep(.el-pager li:not(.is-active):hover) {
  color: var(--cd-primary) !important;
  background-color: var(--theme-primary-muted) !important;
  transform: translateY(-1px) scale(1.05);
}

/* 激活态页码 */
.cd-admin-pagination :deep(.el-pager li.is-active) {
  background: var(--cd-primary-gradient) !important;
  color: #ffffff !important;
  border-color: transparent !important;
  box-shadow: 0 4px 10px var(--theme-primary-muted-strong) !important;
  transform: translateY(-1px) scale(1.05);
}

/* 跳转页码区域 */
.cd-admin-pagination :deep(.el-pagination__jump) {
  font-size: 13px;
  color: var(--cd-text-secondary);
  font-weight: 500;
  margin-left: 16px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.cd-admin-pagination :deep(.el-pagination__editor.el-input) {
  width: 50px !important;
  margin: 0 !important;
}

.cd-admin-pagination :deep(.el-pagination__editor.el-input .el-input__wrapper) {
  border-radius: var(--cd-radius-full) !important;
  background-color: var(--cd-bg-white) !important;
  box-shadow: 0 0 0 1px var(--cd-border) inset !important;
  padding: 0 8px !important;
  height: 32px !important;
  transition: var(--cd-transition-fast);
}

.cd-admin-pagination :deep(.el-pagination__editor.el-input .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--cd-primary-light) inset !important;
}

.cd-admin-pagination :deep(.el-pagination__editor.el-input .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--cd-primary) inset, 0 0 0 3px var(--theme-primary-muted) !important;
}
</style>
