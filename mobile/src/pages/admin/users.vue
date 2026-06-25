<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { request, fileApiUrl } from '@/api/http'
import MobileHeader from '@/components/MobileHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'
import MobilePromptDialog from '@/components/MobilePromptDialog.vue'
import { fmtSize } from '@/utils/fileCover'

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

const auth = useAuthStore()
const loading = ref(false)
const users = ref<UserRow[]>([])
const avatarBroken = ref<Record<number, boolean>>({})

const keyword = ref('')
const filterTab = ref<'all' | 'pending' | 'disabled' | 'admin'>('all')

// 操作面板
const actionShow = ref(false)
const selectedUser = ref<UserRow | null>(null)

// 确认弹框与Prompt弹框
const confirmShow = ref(false)
const confirmTitle = ref('')
const confirmMessage = ref('')
const confirmDanger = ref(false)
const confirmAction = ref<() => Promise<void> | void>()

const promptShow = ref(false)
const promptTitle = ref('')
const promptPlaceholder = ref('')
const promptValue = ref('')
const promptMaxlength = ref(32)
const promptAction = ref<(val: string) => Promise<void> | void>()

async function loadUsers() {
  loading.value = true
  try {
    const data = await request<UserRow[]>({ url: '/api/admin/users' })
    users.value = data || []
  } catch {
    /* error handled by api */
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

function roleBadgeClass(role: string) {
  if (role === 'SUPER_ADMIN') return 'super-admin'
  if (role === 'ADMIN') return 'admin'
  return 'user'
}

function roleOrder(role: string) {
  if (role === 'SUPER_ADMIN') return 0
  if (role === 'ADMIN') return 1
  return 2
}

onShow(async () => {
  if (!auth.requireLogin()) return
  if (!auth.isAdmin) {
    uni.showToast({ title: '无管理员权限', icon: 'none' })
    uni.reLaunch({ url: '/pages/disk/index' })
    return
  }
  await loadUsers()
})

const pendingCount = computed(() => users.value.filter(u => u.status === 2).length)

const filteredUsers = computed(() => {
  const list = users.value.filter(u => {
    // 搜索词过滤
    const kw = keyword.value.trim().toLowerCase()
    if (kw && !u.username.toLowerCase().includes(kw) && !(u.nickname && u.nickname.toLowerCase().includes(kw))) {
      return false
    }
    // Tab过滤
    if (filterTab.value === 'pending' && u.status !== 2) return false
    if (filterTab.value === 'disabled' && u.status !== 0) return false
    if (filterTab.value === 'admin' && u.role !== 'ADMIN' && u.role !== 'SUPER_ADMIN') return false
    return true
  })
  return list.sort((a, b) => {
    const diff = roleOrder(a.role) - roleOrder(b.role)
    if (diff !== 0) return diff
    return (b.createTime || '').localeCompare(a.createTime || '')
  })
})

function getAvatarUrl(userId: number) {
  return fileApiUrl(`/api/admin/users/${userId}/avatar`) + `&v=${auth.avatarVersion}`
}

function onAvatarError(userId: number) {
  avatarBroken.value[userId] = true
}

function openActions(user: UserRow) {
  selectedUser.value = user
  actionShow.value = true
}

function closeActions() {
  actionShow.value = false
}

function openConfirm(title: string, message: string, danger: boolean, onConfirm: () => Promise<void> | void) {
  confirmTitle.value = title
  confirmMessage.value = message
  confirmDanger.value = danger
  confirmAction.value = onConfirm
  confirmShow.value = true
}

function handleConfirm() {
  if (confirmAction.value) {
    confirmAction.value()
  }
}

function openPrompt(title: string, placeholder: string, initial: string, maxLen: number, onConfirm: (val: string) => Promise<void> | void) {
  promptTitle.value = title
  promptPlaceholder.value = placeholder
  promptValue.value = initial
  promptMaxlength.value = maxLen
  promptAction.value = onConfirm
  promptShow.value = true
}

function handlePromptConfirm(val: string) {
  if (promptAction.value) {
    promptAction.value(val)
  }
}

// 审批通过
function approveUser() {
  const user = selectedUser.value
  if (!user) return
  closeActions()
  openConfirm('通过注册申请', `确定通过「${user.nickname || user.username}」的注册申请吗？通过后该账号将被激活并分配 3GB 存储空间。`, false, async () => {
    try {
      await request({ url: `/api/admin/registrations/${user.id}/approve`, method: 'POST' })
      uni.showToast({ title: '已通过注册', icon: 'success' })
      await loadUsers()
    } catch {}
  })
}

// 审批拒绝
function rejectUser() {
  const user = selectedUser.value
  if (!user) return
  closeActions()
  openConfirm('拒绝注册申请', `确定拒绝「${user.nickname || user.username}」的注册申请吗？该申请记录将被永久清理。`, true, async () => {
    try {
      await request({ url: `/api/admin/registrations/${user.id}/reject`, method: 'POST' })
      uni.showToast({ title: '已拒绝注册', icon: 'success' })
      await loadUsers()
    } catch {}
  })
}

// 修改配额
function editQuota() {
  const user = selectedUser.value
  if (!user) return
  closeActions()
  const currentGB = (user.storageQuota || 0) / 1024 / 1024 / 1024
  openPrompt('分配存储配额', '输入配额大小(GB)，0表示不限', String(currentGB), 10, async (val) => {
    const gb = Number(val)
    if (Number.isNaN(gb) || gb < 0) {
      uni.showToast({ title: '请输入有效的配额数值', icon: 'none' })
      return
    }
    const quotaBytes = Math.round(gb * 1024 * 1024 * 1024)
    try {
      await request({
        url: `/api/admin/users/${user.id}/quota`,
        method: 'PUT',
        data: { storageQuota: quotaBytes }
      })
      uni.showToast({ title: '配额设置成功', icon: 'success' })
      await loadUsers()
    } catch {}
  })
}

// 重置密码
function resetPassword() {
  const user = selectedUser.value
  if (!user) return
  closeActions()
  openPrompt('重置密码', '请输入至少 6 位的新密码', '', 32, async (val) => {
    const pwd = val.trim()
    if (!pwd || pwd.length < 6) {
      uni.showToast({ title: '密码长度不能小于 6 位', icon: 'none' })
      return
    }
    try {
      await request({
        url: `/api/admin/users/${user.id}/password`,
        method: 'PUT',
        data: { password: pwd }
      })
      uni.showToast({ title: '密码重置成功，已下线该用户', icon: 'success' })
      await loadUsers()
    } catch {}
  })
}

// 切换角色（仅超级管理员）
function toggleRole() {
  const user = selectedUser.value
  if (!user || !user.canAssignRole) return
  closeActions()
  const isCurrentlyAdmin = user.role === 'ADMIN'
  const targetRole = isCurrentlyAdmin ? 'USER' : 'ADMIN'
  const actionText = isCurrentlyAdmin ? '降为普通用户' : '设为管理员'
  openConfirm('修改角色', `确定将用户「${user.nickname || user.username}」${actionText}吗？`, isCurrentlyAdmin, async () => {
    try {
      await request({
        url: `/api/admin/users/${user.id}/role`,
        method: 'PUT',
        data: { role: targetRole }
      })
      uni.showToast({ title: '角色修改成功', icon: 'success' })
      await loadUsers()
    } catch {}
  })
}

// 切换状态
function toggleStatus() {
  const user = selectedUser.value
  if (!user) return
  closeActions()
  const next = user.status === 1 ? 0 : 1
  const actionText = next === 0 ? '禁用' : '启用'
  openConfirm('确认操作', `确定${actionText}用户「${user.nickname || user.username}」？${next === 0 ? '禁用后该用户的所有在线会话将被注销！' : ''}`, next === 0, async () => {
    try {
      await request({
        url: `/api/admin/users/${user.id}/status`,
        method: 'PUT',
        data: { status: next }
      })
      uni.showToast({ title: '操作成功', icon: 'success' })
      await loadUsers()
    } catch {}
  })
}

function formatDate(d?: string) {
  if (!d) return '-'
  const dt = new Date(d)
  if (Number.isNaN(dt.getTime())) return d
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const day = String(dt.getDate()).padStart(2, '0')
  return `${dt.getFullYear()}/${m}/${day}`
}

function statusClass(status: number) {
  if (status === 1) return 'normal'
  if (status === 2) return 'pending'
  return 'disabled'
}
</script>

<template>
  <view class="page">
    <MobileHeader title="用户管理" :subtitle="`${filteredUsers.length} 人`" gradient />

    <view class="filter-section">
      <!-- 搜索栏 -->
      <view class="search-bar">
        <u-icon name="search" size="18" color="#64748b" />
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索昵称或用户名..."
          confirm-type="search"
        />
        <view v-if="keyword" class="clear-btn cd-pressable" @click="keyword = ''">
          <u-icon name="close-circle-fill" size="16" color="#94a3b8" />
        </view>
      </view>

      <!-- 快速过滤 tab -->
      <view class="filter-tabs">
        <view
          class="tab-chip cd-pressable"
          :class="{ active: filterTab === 'all' }"
          @click="filterTab = 'all'"
        >
          <text class="tab-chip-text">全部</text>
        </view>
        <view
          class="tab-chip cd-pressable"
          :class="{ active: filterTab === 'pending' }"
          @click="filterTab = 'pending'"
        >
          <text class="tab-chip-text">待审核</text>
          <view v-if="pendingCount > 0" class="chip-badge">
            <text>{{ pendingCount }}</text>
          </view>
        </view>
        <view
          class="tab-chip cd-pressable"
          :class="{ active: filterTab === 'disabled' }"
          @click="filterTab = 'disabled'"
        >
          <text class="tab-chip-text">已禁用</text>
        </view>
        <view
          class="tab-chip cd-pressable"
          :class="{ active: filterTab === 'admin' }"
          @click="filterTab = 'admin'"
        >
          <text class="tab-chip-text">管理员</text>
        </view>
      </view>
    </view>

    <!-- 列表展示 -->
    <scroll-view scroll-y class="scroll">
      <view v-if="loading" class="state-box">
        <u-loading-icon text="加载中..." color="var(--cd-primary)" />
      </view>
      <view v-else-if="!filteredUsers.length" class="state-box">
        <EmptyState
          icon="account"
          title="未找到匹配用户"
          description="可以尝试更换检索关键词或更改筛选标签"
        />
      </view>
      <view v-else class="list-wrapper">
        <view
          v-for="user in filteredUsers"
          :key="user.id"
          class="user-card cd-pressable"
          @click="openActions(user)"
        >
          <view class="user-card-main">
            <!-- 头像 -->
            <view class="user-avatar-wrap">
              <image
                v-if="user.hasAvatar && !avatarBroken[user.id]"
                :src="getAvatarUrl(user.id)"
                class="user-avatar"
                mode="aspectFill"
                @error="onAvatarError(user.id)"
              />
              <view v-else class="user-avatar-inner">
                {{ (user.nickname || user.username || 'U').charAt(0).toUpperCase() }}
              </view>
            </view>

            <!-- 用户基础元数据 -->
            <view class="user-info">
              <view class="user-name-row">
                <text class="user-nickname">{{ user.nickname || user.username }}</text>
                <text class="user-username">@{{ user.username }}</text>
              </view>
              <view class="user-meta">
                <text class="meta-item">已用: {{ fmtSize(user.storageUsed || 0) }}</text>
                <text class="meta-divider">·</text>
                <text class="meta-item">配额: {{ user.storageQuota ? fmtSize(user.storageQuota) : '不限' }}</text>
              </view>
              <view class="user-date">
                <text>创建于 {{ formatDate(user.createTime) }}</text>
              </view>
            </view>

            <!-- 用户角色与状态 -->
            <view class="user-status-column">
              <text class="role-badge" :class="roleBadgeClass(user.role)">
                {{ roleLabel(user.role) }}
              </text>
              <text class="status-badge" :class="statusClass(user.status)">
                {{ user.status === 1 ? '正常' : user.status === 2 ? '待审核' : '已禁用' }}
              </text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 底部操作面板 -->
    <view v-if="actionShow" class="drawer-root" @touchmove.stop.prevent>
      <view class="drawer-mask" @click="closeActions" />
      <view class="drawer-panel cd-slide-up" @click.stop>
        <view class="drawer-handle" />

        <view v-if="selectedUser" class="drawer-header">
          <view class="drawer-avatar-wrap">
            <image
              v-if="selectedUser.hasAvatar && !avatarBroken[selectedUser.id]"
              :src="getAvatarUrl(selectedUser.id)"
              class="drawer-avatar"
              mode="aspectFill"
            />
            <view v-else class="drawer-avatar-inner">
              {{ (selectedUser.nickname || selectedUser.username || 'U').charAt(0).toUpperCase() }}
            </view>
          </view>
          <view class="drawer-user-info">
            <text class="drawer-nickname">{{ selectedUser.nickname || selectedUser.username }}</text>
            <text class="drawer-username">@{{ selectedUser.username }}</text>
          </view>
        </view>

        <view v-if="selectedUser" class="drawer-actions">
          <!-- 待审核用户操作 -->
          <block v-if="selectedUser.status === 2 && selectedUser.canApprove">
            <view class="action-item cd-pressable" @click="approveUser">
              <view class="action-icon-box green">
                <u-icon name="checkmark" size="18" color="#10b981" bold />
              </view>
              <text class="action-text text-success">通过注册申请</text>
            </view>
            <view class="action-item cd-pressable" @click="rejectUser">
              <view class="action-icon-box red">
                <u-icon name="close" size="18" color="#ef4444" bold />
              </view>
              <text class="action-text text-danger">拒绝注册申请</text>
            </view>
          </block>

          <!-- 正常/禁用用户操作 -->
          <block v-else-if="selectedUser.canManage">
            <view class="action-item cd-pressable" @click="editQuota">
              <view class="action-icon-box blue">
                <u-icon name="coupon" size="18" color="var(--cd-primary)" bold />
              </view>
              <text class="action-text">修改容量配额</text>
              <text class="action-val">{{ selectedUser.storageQuota ? fmtSize(selectedUser.storageQuota) : '不限' }}</text>
            </view>

            <view class="action-item cd-pressable" @click="resetPassword">
              <view class="action-icon-box violet">
                <u-icon name="lock" size="18" color="#7c3aed" bold />
              </view>
              <text class="action-text">重置登录密码</text>
            </view>

            <view v-if="selectedUser.canAssignRole" class="action-item cd-pressable" @click="toggleRole">
              <view class="action-icon-box sky">
                <u-icon name="account" size="18" color="#0ea5e9" bold />
              </view>
              <text class="action-text">修改角色</text>
              <text class="action-val">{{ roleLabel(selectedUser.role) }}</text>
            </view>

            <view class="action-item cd-pressable" @click="toggleStatus">
              <block v-if="selectedUser.status === 1">
                <view class="action-icon-box red">
                  <u-icon name="minus-circle" size="18" color="#ef4444" bold />
                </view>
                <text class="action-text text-danger">禁用该账号</text>
              </block>
              <block v-else>
                <view class="action-icon-box green">
                  <u-icon name="plus-circle" size="18" color="#10b981" bold />
                </view>
                <text class="action-text text-success">启用该账号</text>
              </block>
            </view>
          </block>
        </view>

        <view class="drawer-footer cd-pressable" @click="closeActions">
          <text>关闭</text>
        </view>
      </view>
    </view>

    <!-- 弹框控制 -->
    <MobileConfirmDialog
      v-model:show="confirmShow"
      :title="confirmTitle"
      :message="confirmMessage"
      :danger="confirmDanger"
      @confirm="handleConfirm"
    />

    <MobilePromptDialog
      v-model:show="promptShow"
      :title="promptTitle"
      :placeholder="promptPlaceholder"
      :initialValue="promptValue"
      :maxlength="promptMaxlength"
      @confirm="handlePromptConfirm"
    />

  </view>
</template>

<style scoped lang="scss">
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--cd-bg);
}

/* 过滤与搜索区域 */
.filter-section {
  padding: 16rpx 24rpx;
  background: var(--cd-bg);
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.search-bar {
  display: flex;
  align-items: center;
  height: 80rpx;
  padding: 0 24rpx;
  border-radius: 20rpx;
  background: #f1f5f9;
  border: 1rpx solid var(--cd-border-light);

  .search-input {
    flex: 1;
    height: 100%;
    margin-left: 12rpx;
    font-size: 26rpx;
    color: var(--cd-text);
  }

  .clear-btn {
    padding: 10rpx;
  }
}

.filter-tabs {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.tab-chip {
  position: relative;
  height: 60rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: var(--cd-bg-card);
  border: 1rpx solid var(--cd-border-light);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;

  .tab-chip-text {
    font-size: 24rpx;
    font-weight: 600;
    color: var(--cd-text-secondary);
  }

  &.active {
    background: var(--cd-primary-bg);
    border-color: var(--cd-primary-light);
    .tab-chip-text {
      color: var(--cd-primary);
      font-weight: 700;
    }
  }

  .chip-badge {
    position: absolute;
    top: -6rpx;
    right: -6rpx;
    background: #ef4444;
    min-width: 28rpx;
    height: 28rpx;
    border-radius: 999rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 2rpx solid #fff;
    padding: 0 4rpx;

    text {
      font-size: 16rpx;
      font-weight: 800;
      color: #fff;
      line-height: 1;
    }
  }
}

/* 列表滚动区 */
.scroll {
  flex: 1;
  min-height: 0;
}

.state-box {
  padding: 80rpx 0;
  display: flex;
  justify-content: center;
  align-items: center;
}

.list-wrapper {
  padding: 12rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

/* 用户卡片 */
.user-card {
  background: var(--cd-bg-card);
  border: 1rpx solid var(--cd-border-light);
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: var(--cd-shadow-sm);
  transition: transform 0.2s, box-shadow 0.2s;

  &:active {
    transform: scale(0.98);
    box-shadow: var(--cd-shadow-xs);
    background: var(--cd-bg-surface);
  }
}

.user-card-main {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.user-avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.user-avatar, .user-avatar-inner {
  width: 90rpx;
  height: 90rpx;
  border-radius: 999rpx;
}

.user-avatar {
  background: #f1f5f9;
  display: block;
}

.user-avatar-inner {
  background: var(--cd-primary-gradient);
  color: #ffffff;
  font-size: 38rpx;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.user-name-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.user-nickname {
  font-size: 28rpx;
  font-weight: 750;
  color: var(--cd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-username {
  font-size: 22rpx;
  color: var(--cd-text-placeholder);
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 22rpx;
  color: var(--cd-text-secondary);
}

.meta-divider {
  color: #cbd5e1;
}

.user-date {
  font-size: 20rpx;
  color: var(--cd-text-muted);
}

.user-status-column {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10rpx;
}

.role-badge {
  font-size: 18rpx;
  font-weight: 700;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  border: 1rpx solid transparent;

  &.admin {
    background: rgba(245, 158, 11, 0.08);
    color: #d97706;
    border-color: rgba(245, 158, 11, 0.2);
  }

  &.super-admin {
    background: rgba(239, 68, 68, 0.08);
    color: #dc2626;
    border-color: rgba(239, 68, 68, 0.2);
  }

  &.user {
    background: rgba(100, 116, 139, 0.08);
    color: #64748b;
    border-color: rgba(100, 116, 139, 0.15);
  }
}

.status-badge {
  font-size: 20rpx;
  font-weight: 700;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;

  &.normal {
    background: rgba(16, 185, 129, 0.08);
    color: #10b981;
  }

  &.pending {
    background: rgba(249, 115, 22, 0.08);
    color: #f97316;
  }

  &.disabled {
    background: rgba(239, 68, 68, 0.08);
    color: #ef4444;
  }
}

/* 底部抽屉组件 */
.drawer-root {
  position: fixed;
  inset: 0;
  z-index: 1001;
}

.drawer-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(4rpx);
}

.drawer-panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  border-radius: 36rpx 36rpx 0 0;
  padding: 20rpx 0 calc(env(safe-area-inset-bottom) + 16rpx);
  box-shadow: 0 -12rpx 36rpx rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
}

.drawer-handle {
  width: 72rpx;
  height: 8rpx;
  background: #cbd5e1;
  border-radius: 999rpx;
  margin: 0 auto 24rpx;
}

.drawer-header {
  padding: 0 40rpx 28rpx;
  border-bottom: 1rpx solid var(--cd-border-light);
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.drawer-avatar-wrap {
  flex-shrink: 0;
}

.drawer-avatar, .drawer-avatar-inner {
  width: 80rpx;
  height: 80rpx;
  border-radius: 999rpx;
}

.drawer-avatar {
  background: #f1f5f9;
  display: block;
}

.drawer-avatar-inner {
  background: var(--cd-primary-gradient);
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}

.drawer-user-info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.drawer-nickname {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--cd-text);
}

.drawer-username {
  font-size: 24rpx;
  color: var(--cd-text-placeholder);
}

.drawer-actions {
  padding: 16rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 20rpx;
  border-radius: 20rpx;
  transition: background 0.15s;

  &:active {
    background: #f8fafc;
  }

  .action-text {
    flex: 1;
    font-size: 28rpx;
    font-weight: 700;
    color: var(--cd-text-secondary);
  }

  .action-val {
    font-size: 24rpx;
    font-weight: 600;
    color: var(--cd-text-placeholder);
  }

  .text-success {
    color: #10b981;
  }

  .text-danger {
    color: #ef4444;
  }
}

.action-icon-box {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;

  &.green { background: rgba(16, 185, 129, 0.08); }
  &.red { background: rgba(239, 68, 68, 0.08); }
  &.blue { background: rgba(37, 99, 235, 0.08); }
  &.violet { background: rgba(124, 58, 237, 0.08); }
  &.sky { background: rgba(14, 165, 233, 0.08); }
}

.drawer-footer {
  margin-top: 12rpx;
  border-top: 1rpx solid var(--cd-border-light);
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--cd-text-secondary);

  &:active {
    background: #f8fafc;
  }
}
</style>
