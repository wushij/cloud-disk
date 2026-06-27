<script setup lang="ts">

import { computed, ref, onMounted, onUnmounted, watch } from 'vue'

import { useRoute, useRouter } from 'vue-router'

import { storeToRefs } from 'pinia'

import { useAuthStore } from '@/stores/auth'

import { useNotificationStore } from '@/stores/notification'

import ThemePicker from '@/components/ThemePicker.vue'
import BrandMark from '@/components/BrandMark.vue'

import { useTransferStore } from '@/stores/transfer'

import TransferPanel from '@/components/TransferPanel.vue'
import PromptDialog from '@/components/PromptDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

import TeamSpaceIcon from '@/components/icons/TeamSpaceIcon.vue'

import http from '@/api/http'

import { ElMessage, ElMessageBox } from 'element-plus'
import { useConfirmDialogStore } from '@/stores/confirmDialog'

import { subscribeWs } from '@/utils/ws'
import { useFileStore } from '@/stores/file'



const route = useRoute()

const router = useRouter()

const auth = useAuthStore()
const fileStore = useFileStore()

const notifyStore = useNotificationStore()

const transferStore = useTransferStore()
const confirmDialog = useConfirmDialogStore()

const { runningCount: runningTransfersCount } = storeToRefs(transferStore)

const storageLabel = ref('')

function toggleTransferList() {
  if (transferStore.tasks.length === 0) {
    ElMessage.info('暂无传输任务')
    return
  }
  transferStore.toggleCollapse()
}



interface StorageUsage {

  usedBytes: number

  quotaBytes: number

  usedPercent: number

  usedFormatted: string

  quotaFormatted: string

}

const storageUsage = ref<StorageUsage | null>(null)



const notifyVisible = ref(false)

const avatarBroken = ref(false)

const showHeaderAvatar = computed(
  () => !!auth.avatarDisplaySrc && !avatarBroken.value
)

const navItems = [
  { path: '/disk', label: '我的云盘', icon: 'FolderOpened' },
  { path: '/shares', label: '我的分享', icon: 'Share' },
  { path: '/teams', label: '团队空间', icon: TeamSpaceIcon },
  { path: '/recycle', label: '回收站', icon: 'Delete' },
  { path: '/profile', label: '个人中心', icon: 'User' },
  { path: '/admin/users', label: '用户管理', icon: 'UserFilled', admin: true as const },
  { path: '/admin', label: '系统管理', icon: 'Setting', admin: true as const }
]

watch(() => auth.avatarVersion, () => {
  avatarBroken.value = false
})

function onHeaderAvatarError() {
  avatarBroken.value = true
  auth.markAvatarUnavailable()
}

const active = computed(() => route.path)

const pageTitle = computed(() => {
  const item = navItems.find((i) => route.path.startsWith(i.path))
  return item?.label ?? 'CloudDisk Pro'
})

const visibleNavItems = computed(() =>
  navItems.filter((i) => !('admin' in i && i.admin) || auth.isAdmin)
)

const unread = computed(() => notifyStore.unreadCount())



let unsubscribeWs: (() => void) | null = null



onMounted(async () => {

  try {

    await Promise.all([
      auth.fetchProfile(),
      auth.ensureMediaToken()
    ])

  } catch {

    /* ignore */

  }

  try {

    const { data } = await http.get('/api/storage/info')

    if (data.type === 'minio') {

      storageLabel.value = `MinIO · ${data.bucket}`

    } else {

      storageLabel.value = '本地存储'

    }

  } catch {

    storageLabel.value = ''

  }

  try {

    const { data } = await http.get('/api/storage/usage')

    storageUsage.value = data

  } catch {

    /* ignore */

  }

  try {

    await notifyStore.loadFromApi()

  } catch {

    /* ignore */

  }



  unsubscribeWs = subscribeWs((data) => {
    if (data.type === 'notification') {
      notifyStore.push({
        id: data.notifyId,
        type: data.notifyType,
        title: data.title,
        content: data.content,
        refId: data.refId,
        inviteStatus: data.inviteStatus as any,
        registrationStatus: data.registrationStatus as any,
        quotaStatus: data.quotaStatus as any
      })
      if (data.notifyType === 'ROLE_CHANGED' || data.notifyType === 'QUOTA_RESULT') {
        auth.fetchProfile().catch(() => {})
        refreshStorageUsage()
      }
      if (data.notifyType === 'TRANSCODE_DONE') {
        fileStore.onTranscodeEvent(data.refId)
      }
    }
  })

})



onUnmounted(() => {

  unsubscribeWs?.()

})



async function logout() {
  await auth.logout()
  router.push('/login')
}

function onUserCommand(cmd: string) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    logout()
  }
}



const detailVisible = ref(false)
const selectedNotify = ref<any>(null)

async function openNotify(item: any) {
  selectedNotify.value = { ...item }
  detailVisible.value = true
  if (!item.read) {
    await notifyStore.markRead(item.id)
  }
}

function getNotifyIcon(type: string, title: string) {
  if (type === 'TEAM_INVITED') return 'User'
  if (type === 'USER_REGISTER') return 'UserFilled'
  if (type === 'QUOTA_APPLY' || type === 'QUOTA_RESULT' || type === 'ROLE_CHANGED') return 'UploadFilled'
  if (type === 'SHARE_EXPIRED' || title.includes('分享') || title.includes('外链')) return 'Share'
  return 'Bell'
}

function getNotifyColorClass(type: string, title: string) {
  if (type === 'TEAM_INVITED') return 'teal'
  if (type === 'USER_REGISTER') return 'blue'
  if (type === 'QUOTA_APPLY') return 'orange'
  if (type === 'QUOTA_RESULT' || type === 'ROLE_CHANGED') return 'teal'
  if (type === 'SHARE_EXPIRED' || title.includes('分享') || title.includes('外链')) return 'orange'
  return 'indigo'
}

function formatTime(createdAt: number) {
  if (!createdAt) return ''
  const d = new Date(createdAt)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function isInvitePending(status?: string) {
  return !status || status === 'PENDING'
}

function isRegistrationPending(status?: string) {
  return !status || status === 'PENDING'
}

function getRegistrationSubject(content: string) {
  const match = content.match(/^(.+?)（.+?）申请注册/)
  return match ? match[1] : '该用户'
}

function getTeamName(content: string) {
  const match = content.match(/加入团队「(.+?)」/)
  return match ? match[1] : '该团队'
}

function syncNotifyItem(item: { id: string }, patch: Record<string, unknown>) {
  const storeItem = notifyStore.items.find(x => x.id === item.id)
  if (storeItem) Object.assign(storeItem, patch)
  if (selectedNotify.value?.id === item.id) Object.assign(selectedNotify.value, patch)
}

async function acceptTeamInvite(item: { id: string; refId?: string; content?: string }) {
  if (!item.refId) return
  const teamName = getTeamName(item.content || '')
  const ok = await confirmDialog.open({
    title: '接受团队邀请',
    message: `确定加入团队「${teamName}」吗？加入后可在团队空间中查看和协作。`,
    confirmText: '接受'
  })
  if (!ok) return
  try {
    await notifyStore.acceptTeamInvite(item as any)
    syncNotifyItem(item, {
      title: '已接受邀请',
      content: '你已成功加入团队，现在可以在团队空间中查看和协作。',
      inviteStatus: 'ACCEPTED',
      read: true
    })
    await confirmDialog.openAlert({
      title: '已加入团队',
      message: `你已成功加入「${teamName}」，可在侧边栏进入团队空间。`,
      confirmText: '好的',
      tone: 'success'
    })
  } catch {
    /* global toast */
  }
}

async function rejectTeamInvite(item: { id: string; refId?: string; content?: string }) {
  if (!item.refId) return
  const teamName = getTeamName(item.content || '')
  const ok = await confirmDialog.open({
    title: '拒绝团队邀请',
    message: `确定拒绝加入团队「${teamName}」吗？`,
    confirmText: '拒绝',
    danger: true
  })
  if (!ok) return
  try {
    await notifyStore.rejectTeamInvite(item as any)
    syncNotifyItem(item, {
      title: '已拒绝邀请',
      content: '你已拒绝该团队的邀请。',
      inviteStatus: 'REJECTED',
      read: true
    })
    await confirmDialog.openAlert({
      title: '已拒绝邀请',
      message: `你已拒绝加入「${teamName}」。`,
      confirmText: '我知道了',
      tone: 'info'
    })
  } catch {
    /* global toast */
  }
}

async function approveRegistration(item: { id: string; refId?: string; content?: string }) {
  if (!item.refId) return
  const subject = getRegistrationSubject(item.content || '')
  const ok = await confirmDialog.open({
    title: '通过注册申请',
    message: `确定通过「${subject}」的注册申请吗？通过后该账号将被激活并分配 3GB 存储空间。`,
    confirmText: '通过'
  })
  if (!ok) return
  try {
    await notifyStore.approveRegistration(item as any)
    syncNotifyItem(item, {
      title: '已通过注册',
      content: '该用户现在可以登录使用云盘（3GB 空间）。',
      registrationStatus: 'APPROVED',
      read: true
    })
    await confirmDialog.openAlert({
      title: '已通过注册',
      message: `「${subject}」的账号已激活，可正常登录使用云盘。`,
      confirmText: '好的',
      tone: 'success'
    })
  } catch {
    /* global toast */
  }
}

async function rejectRegistration(item: { id: string; refId?: string; content?: string }) {
  if (!item.refId) return
  const subject = getRegistrationSubject(item.content || '')
  const ok = await confirmDialog.open({
    title: '拒绝注册申请',
    message: `确定拒绝「${subject}」的注册申请吗？该用户将无法登录云盘。`,
    confirmText: '拒绝',
    danger: true
  })
  if (!ok) return
  try {
    await notifyStore.rejectRegistration(item as any)
    syncNotifyItem(item, {
      title: '已拒绝注册',
      content: '该用户的注册申请已被拒绝。',
      registrationStatus: 'REJECTED',
      read: true
    })
    await confirmDialog.openAlert({
      title: '已拒绝注册',
      message: `已拒绝「${subject}」的注册申请。`,
      confirmText: '我知道了',
      tone: 'info'
    })
  } catch {
    /* global toast */
  }
}

async function handleAcceptInDetail(item: any) {
  await acceptTeamInvite(item)
}

async function handleRejectInDetail(item: any) {
  await rejectTeamInvite(item)
}

async function handleApproveRegistrationInDetail(item: any) {
  await approveRegistration(item)
}

async function handleRejectRegistrationInDetail(item: any) {
  await rejectRegistration(item)
}

async function deleteNotify(item: any) {
  const ok = await confirmDialog.open({
    title: '删除通知',
    message: '确定删除这条通知吗？',
    confirmText: '删除',
    danger: true
  })
  if (!ok) return
  try {
    await notifyStore.remove(item.id)
    ElMessage.success('已删除通知')
  } catch {
    /* global toast */
  }
}


async function handleClearAll() {
  const ok = await confirmDialog.open({
    title: '清空通知',
    message: '确定清空所有通知吗？清空后所有消息将被永久删除，且不可恢复。',
    confirmText: '确认清空',
    danger: true
  })
  if (!ok) return
  await notifyStore.clearAll()
  ElMessage.success('已清空所有通知')
}

const applyQuotaVisible = ref(false)
const applyQuotaGB = ref('')
const applyReason = ref('')
const applySaving = ref(false)

const canApplyQuota = computed(
  () => !auth.isSuperAdmin && storageUsage.value != null && storageUsage.value.quotaBytes > 0
)

function openApplyQuotaDialog() {
  applyQuotaVisible.value = true
  applyQuotaGB.value = ''
  applyReason.value = ''
}

async function submitApplyQuota() {
  if (!applyQuotaGB.value) {
    ElMessage.warning('请输入目标容量')
    return
  }
  const gb = Number(applyQuotaGB.value)
  if (isNaN(gb) || gb <= 0) {
    ElMessage.warning('容量大小必须大于 0')
    return
  }
  const quotaBytes = Math.round(gb * 1024 * 1024 * 1024)
  if (storageUsage.value && quotaBytes <= storageUsage.value.quotaBytes) {
    ElMessage.warning('申请配额必须大于当前配额')
    return
  }

  applySaving.value = true
  try {
    await http.post('/api/quota-applications', {
      applyQuota: quotaBytes,
      reason: applyReason.value
    })
    ElMessage.success('扩容申请已提交，请等待管理员审批')
    applyQuotaVisible.value = false
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || err.message || '申请提交失败')
  } finally {
    applySaving.value = false
  }
}

function isQuotaPending(status?: string) {
  return !status || status === 'PENDING'
}

async function refreshStorageUsage() {
  try {
    const { data } = await http.get('/api/storage/usage')
    storageUsage.value = data
  } catch {
    /* ignore */
  }
}

async function approveQuota(item: { id: string; refId?: string; content?: string }) {
  if (!item.refId) return
  let opinion = ''
  try {
    const res = await ElMessageBox.prompt('请输入审批意见（选填）', '通过扩容申请', {
      confirmButtonText: '确定通过',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入意见或理由...',
      inputValue: '通过'
    })
    opinion = res.value || ''
  } catch {
    return
  }

  try {
    await notifyStore.approveQuota(item as any, opinion)
    syncNotifyItem(item, {
      title: '已通过扩容',
      content: `扩容申请已被你通过。审批意见：${opinion || '无'}`,
      quotaStatus: 'APPROVED',
      read: true
    })
    ElMessage.success('扩容申请已通过')
    await refreshStorageUsage()
  } catch {
    /* error handled */
  }
}

async function rejectQuota(item: { id: string; refId?: string; content?: string }) {
  if (!item.refId) return
  let opinion = ''
  try {
    const res = await ElMessageBox.prompt('请输入拒绝原因（必填）', '拒绝扩容申请', {
      confirmButtonText: '确定拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入拒绝原因...',
      inputValidator: (val) => {
        if (!val || !val.trim()) return '请输入拒绝原因'
        return true
      }
    })
    opinion = res.value || ''
  } catch {
    return
  }

  try {
    await notifyStore.rejectQuota(item as any, opinion)
    syncNotifyItem(item, {
      title: '已拒绝扩容',
      content: `扩容申请已被你拒绝。拒绝原因：${opinion}`,
      quotaStatus: 'REJECTED',
      read: true
    })
    ElMessage.success('扩容申请已拒绝')
  } catch {
    /* error handled */
  }
}

</script>



<template>

  <el-container class="cd-layout">

    <!-- 深色渐变侧边栏 -->

    <el-aside width="240px" class="layout-sidebar cd-sidebar">

      <!-- Logo -->

      <div class="cd-sidebar-logo">

        <BrandMark :size="24" class="cd-logo-icon" />

        <span class="cd-logo-text">CloudDisk Pro</span>

      </div>



      <!-- 导航菜单 -->

      <nav class="cd-nav">

        <router-link

          v-for="item in visibleNavItems"

          :key="item.path"

          :to="item.path"

          class="cd-nav-item"

          :class="{ active: active === item.path }"

        >

          <el-icon :size="20"><component :is="item.icon" /></el-icon>

          <span>{{ item.label }}</span>

          <div v-if="active === item.path" class="cd-nav-indicator" />

        </router-link>

      </nav>



      <!-- 底部存储信息 -->
      <div class="cd-sidebar-footer">
        <div v-if="storageUsage" class="cd-storage-card">
          <div class="cd-storage-head">
            <span class="cd-storage-head-label">存储空间</span>
            <span v-if="storageUsage.quotaBytes === 0" class="cd-storage-badge">无限容量</span>
            <span v-else class="cd-storage-percent">{{ storageUsage.usedPercent }}%</span>
          </div>

          <div class="cd-storage-usage">
            <strong>{{ storageUsage.usedFormatted }}</strong>
            <span v-if="storageUsage.quotaBytes > 0" class="cd-storage-quota">
              / {{ storageUsage.quotaFormatted }}
            </span>
          </div>

          <el-progress
            v-if="storageUsage.quotaBytes > 0"
            :percentage="storageUsage.usedPercent"
            :stroke-width="5"
            :color="storageUsage.usedPercent >= 90 ? '#ef4444' : storageUsage.usedPercent >= 70 ? '#f59e0b' : 'var(--theme-primary)'"
            :show-text="false"
            class="cd-storage-progress"
          />

          <div class="cd-storage-footer">
            <div v-if="storageLabel" class="cd-storage-type">
              <el-icon :size="12"><Monitor /></el-icon>
              <span>{{ storageLabel }}</span>
            </div>
            <div v-if="canApplyQuota" class="cd-storage-apply-btn" @click="openApplyQuotaDialog">
              <span>申请扩容</span>
            </div>
          </div>
        </div>
      </div>

    </el-aside>



    <el-container class="cd-main-container">

      <!-- 毛玻璃 Header -->

      <el-header class="cd-header">

        <div class="cd-header-left">

          <h1 class="cd-page-title">{{ pageTitle }}</h1>

        </div>

        <div class="cd-header-right">

          <div class="cd-header-transfer-wrap" @click="toggleTransferList">
            <button class="cd-header-btn cd-transfer-btn" :class="{ active: runningTransfersCount > 0 }">
              <svg viewBox="0 0 24 24" fill="none" class="cd-transfer-btn-icon">
                <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                <path d="M6.5 11L9 8.5L11.5 11M9 8.5V16.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M12.5 13.5L15 16L17.5 13.5M15 8V16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
            <span v-if="runningTransfersCount > 0" class="cd-transfer-btn-badge">
              {{ runningTransfersCount > 99 ? '99+' : runningTransfersCount }}
            </span>
          </div>

          <div class="cd-notify-wrap" @click="notifyVisible = true">
            <button class="cd-header-btn cd-notify-btn">
              <el-icon :size="18"><Bell /></el-icon>
            </button>
            <span v-if="unread > 0" class="cd-notify-dot">
              {{ unread > 99 ? '99+' : unread }}
            </span>
          </div>

          <ThemePicker />

          <el-dropdown trigger="click" @command="onUserCommand">

            <div class="cd-user-info">

              <div
                v-if="auth.hasAvatar && !auth.avatarDisplaySrc && !avatarBroken"
                class="cd-user-avatar cd-user-avatar-skeleton"
                aria-hidden="true"
              />
              <el-avatar
                v-else
                :key="auth.avatarSrc"
                :size="32"
                :src="showHeaderAvatar ? auth.avatarDisplaySrc : undefined"
                class="cd-user-avatar"
                @error="onHeaderAvatarError"
              >

                {{ auth.avatarInitial }}

              </el-avatar>

              <span class="cd-user-name">{{ auth.nickname || auth.username }}</span>

              <el-icon :size="14" class="cd-user-arrow"><ArrowDown /></el-icon>

            </div>

            <template #dropdown>

              <el-dropdown-menu>

                <el-dropdown-item command="profile">

                  <el-icon><User /></el-icon> 个人中心

                </el-dropdown-item>

                <el-dropdown-item divided command="logout">

                  <el-icon><SwitchButton /></el-icon> 退出登录

                </el-dropdown-item>

              </el-dropdown-menu>

            </template>

          </el-dropdown>

        </div>

      </el-header>



      <!-- 主内容区 -->

      <el-main class="cd-main">

        <router-view v-slot="{ Component, route }">
          <transition name="fade" mode="out-in">
            <keep-alive v-if="route.meta.keepAlive">
              <component :is="Component" :key="route.name" />
            </keep-alive>
            <component v-else :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>

      </el-main>

    </el-container>



    <!-- 通知抽屉 -->

    <el-drawer v-model="notifyVisible" title="消息通知" size="380px">

      <template #header>

        <div class="cd-notify-header">

          <span class="cd-notify-title">消息通知</span>

          <div class="cd-notify-header-actions">
            <el-button size="small" text @click="notifyStore.markAllRead()">全部已读</el-button>
            <el-divider direction="vertical" />
            <el-button v-if="notifyStore.items.length" size="small" text type="danger" @click="handleClearAll">清空</el-button>
          </div>

        </div>

      </template>

      <el-empty v-if="!notifyStore.items.length" description="暂无通知" />

      <div v-else class="cd-notify-list">

        <div
          v-for="item in notifyStore.items"
          :key="item.id"
          class="cd-notify-item"
          :class="{ unread: !item.read }"
          @click="openNotify(item)"
        >
          <div class="cd-notify-icon-box" :class="getNotifyColorClass(item.type, item.title)">
            <el-icon :size="16">
              <component :is="getNotifyIcon(item.type, item.title)" />
            </el-icon>
          </div>
          <div class="cd-notify-content">
            <div class="cd-notify-item-title-row">
              <span class="cd-notify-item-title">{{ item.title }}</span>
              <div class="cd-notify-item-right-meta">
                <span class="cd-notify-item-time">{{ formatTime(item.createdAt) }}</span>
                <el-button
                  class="cd-notify-delete-btn"
                  size="small"
                  link
                  type="danger"
                  @click.stop="deleteNotify(item)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
            <div class="cd-notify-item-text">{{ item.content }}</div>
            <div
              v-if="item.type === 'TEAM_INVITED' && item.refId && isInvitePending(item.inviteStatus)"
              class="cd-notify-actions"
              @click.stop
            >
              <button type="button" class="cd-notify-action-pill cd-notify-action-pill--ghost" @click="rejectTeamInvite(item)">拒绝</button>
              <button type="button" class="cd-notify-action-pill cd-notify-action-pill--primary" @click="acceptTeamInvite(item)">接受</button>
            </div>
            <div
              v-else-if="item.type === 'TEAM_INVITED' && item.refId && item.inviteStatus === 'ACCEPTED'"
              class="cd-notify-status-row"
            >
              <span class="invite-status-badge accepted">已接受邀请</span>
            </div>
            <div
              v-else-if="item.type === 'TEAM_INVITED' && item.refId && (item.inviteStatus === 'REJECTED' || item.inviteStatus === 'EXPIRED')"
              class="cd-notify-status-row"
            >
              <span class="invite-status-badge rejected">
                {{ item.inviteStatus === 'REJECTED' ? '已拒绝邀请' : '已失效' }}
              </span>
            </div>
            <div
              v-if="auth.isAdmin && item.type === 'USER_REGISTER' && item.refId && isRegistrationPending(item.registrationStatus)"
              class="cd-notify-actions"
              @click.stop
            >
              <button type="button" class="cd-notify-action-pill cd-notify-action-pill--ghost" @click="rejectRegistration(item)">拒绝</button>
              <button type="button" class="cd-notify-action-pill cd-notify-action-pill--primary" @click="approveRegistration(item)">通过</button>
            </div>
            <div
              v-else-if="auth.isAdmin && item.type === 'USER_REGISTER' && item.refId && item.registrationStatus === 'APPROVED'"
              class="cd-notify-status-row"
            >
              <span class="invite-status-badge accepted">已通过注册</span>
            </div>
            <div
              v-else-if="auth.isAdmin && item.type === 'USER_REGISTER' && item.refId && item.registrationStatus === 'REJECTED'"
              class="cd-notify-status-row"
            >
              <span class="invite-status-badge rejected">已拒绝注册</span>
            </div>
            <!-- QUOTA APPLY WORKFLOW -->
            <div
              v-if="auth.isSuperAdmin && item.type === 'QUOTA_APPLY' && item.refId && isQuotaPending(item.quotaStatus)"
              class="cd-notify-actions"
              @click.stop
            >
              <button type="button" class="cd-notify-action-pill cd-notify-action-pill--ghost" @click="rejectQuota(item)">拒绝</button>
              <button type="button" class="cd-notify-action-pill cd-notify-action-pill--primary" @click="approveQuota(item)">通过</button>
            </div>
            <div
              v-else-if="auth.isSuperAdmin && item.type === 'QUOTA_APPLY' && item.refId && item.quotaStatus === 'APPROVED'"
              class="cd-notify-status-row"
            >
              <span class="invite-status-badge accepted">已通过扩容</span>
            </div>
            <div
              v-else-if="auth.isSuperAdmin && item.type === 'QUOTA_APPLY' && item.refId && item.quotaStatus === 'REJECTED'"
              class="cd-notify-status-row"
            >
              <span class="invite-status-badge rejected">已拒绝扩容</span>
            </div>
          </div>
        </div>

      </div>

    </el-drawer>

    <!-- 消息详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="通知详情"
      width="420px"
      align-center
      destroy-on-close
    >
      <div v-if="selectedNotify" class="cd-notify-detail-content">
        <div class="cd-notify-detail-icon-wrap" :class="getNotifyColorClass(selectedNotify.type, selectedNotify.title)">
          <el-icon :size="32">
            <component :is="getNotifyIcon(selectedNotify.type, selectedNotify.title)" />
          </el-icon>
        </div>
        <h3 class="cd-notify-detail-title">{{ selectedNotify.title }}</h3>
        <div class="cd-notify-detail-time">{{ formatTime(selectedNotify.createdAt) }}</div>
        <div class="cd-notify-detail-body">
          {{ selectedNotify.content }}
        </div>
        <div v-if="selectedNotify.type === 'TEAM_INVITED' && selectedNotify.refId && isInvitePending(selectedNotify.inviteStatus)" class="cd-notify-detail-actions">
          <button type="button" class="cd-notify-action-pill cd-notify-action-pill--ghost cd-notify-action-pill--lg" @click="handleRejectInDetail(selectedNotify)">拒绝邀请</button>
          <button type="button" class="cd-notify-action-pill cd-notify-action-pill--primary cd-notify-action-pill--lg" @click="handleAcceptInDetail(selectedNotify)">接受邀请</button>
        </div>
        <div v-else-if="selectedNotify.type === 'TEAM_INVITED' && selectedNotify.refId && selectedNotify.inviteStatus === 'ACCEPTED'" class="cd-notify-detail-actions detail-actions-col">
          <div class="invite-status-badge large accepted">已接受邀请</div>
        </div>
        <div v-else-if="selectedNotify.type === 'TEAM_INVITED' && selectedNotify.refId && (selectedNotify.inviteStatus === 'REJECTED' || selectedNotify.inviteStatus === 'EXPIRED')" class="cd-notify-detail-actions detail-actions-col">
          <div class="invite-status-badge large rejected">
            {{ selectedNotify.inviteStatus === 'REJECTED' ? '已拒绝邀请' : '邀请已失效' }}
          </div>
        </div>
        <div v-if="auth.isAdmin && selectedNotify.type === 'USER_REGISTER' && selectedNotify.refId && isRegistrationPending(selectedNotify.registrationStatus)" class="cd-notify-detail-actions">
          <button type="button" class="cd-notify-action-pill cd-notify-action-pill--ghost cd-notify-action-pill--lg" @click="handleRejectRegistrationInDetail(selectedNotify)">拒绝注册</button>
          <button type="button" class="cd-notify-action-pill cd-notify-action-pill--primary cd-notify-action-pill--lg" @click="handleApproveRegistrationInDetail(selectedNotify)">通过注册</button>
        </div>
        <div v-else-if="auth.isAdmin && selectedNotify.type === 'USER_REGISTER' && selectedNotify.refId && selectedNotify.registrationStatus === 'APPROVED'" class="cd-notify-detail-actions detail-actions-col">
          <div class="invite-status-badge large accepted">已通过注册</div>
        </div>
        <div v-else-if="auth.isAdmin && selectedNotify.type === 'USER_REGISTER' && selectedNotify.refId && selectedNotify.registrationStatus === 'REJECTED'" class="cd-notify-detail-actions detail-actions-col">
          <div class="invite-status-badge large rejected">已拒绝注册</div>
        </div>
        <!-- QUOTA APPLY WORKFLOW IN DETAIL DIALOG -->
        <div v-if="auth.isSuperAdmin && selectedNotify.type === 'QUOTA_APPLY' && selectedNotify.refId && isQuotaPending(selectedNotify.quotaStatus)" class="cd-notify-detail-actions">
          <button type="button" class="cd-notify-action-pill cd-notify-action-pill--ghost cd-notify-action-pill--lg" @click="rejectQuota(selectedNotify)">拒绝申请</button>
          <button type="button" class="cd-notify-action-pill cd-notify-action-pill--primary cd-notify-action-pill--lg" @click="approveQuota(selectedNotify)">通过扩容</button>
        </div>
        <div v-else-if="auth.isSuperAdmin && selectedNotify.type === 'QUOTA_APPLY' && selectedNotify.refId && selectedNotify.quotaStatus === 'APPROVED'" class="cd-notify-detail-actions detail-actions-col">
          <div class="invite-status-badge large accepted">已通过扩容</div>
        </div>
        <div v-else-if="auth.isSuperAdmin && selectedNotify.type === 'QUOTA_APPLY' && selectedNotify.refId && selectedNotify.quotaStatus === 'REJECTED'" class="cd-notify-detail-actions detail-actions-col">
          <div class="invite-status-badge large rejected">已拒绝扩容</div>
        </div>
      </div>
      <template #footer>
        <div
          v-if="selectedNotify && !(selectedNotify.type === 'TEAM_INVITED' && selectedNotify.refId && isInvitePending(selectedNotify.inviteStatus)) && !(auth.isAdmin && selectedNotify.type === 'USER_REGISTER' && selectedNotify.refId && isRegistrationPending(selectedNotify.registrationStatus)) && !(auth.isSuperAdmin && selectedNotify.type === 'QUOTA_APPLY' && selectedNotify.refId && isQuotaPending(selectedNotify.quotaStatus))"
          class="cd-dialog-footer-pills is-center"
        >
          <el-button size="large" @click="detailVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 全局传输进度面板 -->
    <TransferPanel />
    <PromptDialog />
    <ConfirmDialog />

    <!-- 用户自主扩容申请对话框 -->
    <el-dialog
      v-model="applyQuotaVisible"
      width="440px"
      class="cd-apply-quota-dialog"
      align-center
      destroy-on-close
    >
      <template #header>
        <div class="cd-quota-dialog-header-clean">
          <div class="cd-quota-dialog-icon-clean">
            <el-icon :size="18" color="var(--theme-primary)"><UploadFilled /></el-icon>
          </div>
          <span class="cd-quota-dialog-title-clean">申请容量扩容</span>
        </div>
      </template>

      <div class="cd-quota-hint-banner">
        <span class="hint-text">请填写申请的目标容量（GB）及扩容原因</span>
      </div>

      <div class="cd-form-group">
        <label class="cd-form-label">
          目标容量 (GB)
          <span class="cd-form-label-tip" v-if="storageUsage">
            (当前容量: {{ storageUsage.quotaFormatted }})
          </span>
        </label>
        <el-input
          v-model="applyQuotaGB"
          placeholder="例如 10"
          type="number"
          :min="1"
        >
          <template #suffix>
            <span class="cd-input-suffix-text">GB</span>
          </template>
        </el-input>
      </div>

      <div class="cd-form-group" style="margin-top: 16px;">
        <label class="cd-form-label">申请原因</label>
        <el-input
          v-model="applyReason"
          type="textarea"
          :rows="3"
          placeholder="请输入申请扩容的理由..."
          maxlength="200"
          show-word-limit
        />
      </div>

      <template #footer>
        <div class="cd-dialog-footer-pills">
          <el-button size="large" class="cd-quota-cancel-btn" @click="applyQuotaVisible = false">取消</el-button>
          <el-button type="primary" size="large" :loading="applySaving" class="cd-quota-submit-btn" @click="submitApplyQuota">提交申请</el-button>
        </div>
      </template>
    </el-dialog>

  </el-container>

</template>



<style scoped>

/* ============================================

   布局容器

   ============================================ */

.cd-layout {

  width: 100%;

  height: 100vh;

  min-height: 100vh;

  overflow: hidden;

  background: var(--theme-bg, var(--cd-bg));

}



.cd-main-container {

  flex: 1;

  flex-direction: column;

  min-width: 0;

  min-height: 0;

  overflow: hidden;

}



/* ============================================

   毛玻璃 Header

   ============================================ */

.cd-header {

  background: rgba(255, 255, 255, 0.92);

  backdrop-filter: blur(12px);

  -webkit-backdrop-filter: blur(12px);

  border-bottom: 1px solid var(--cd-border-light);

  display: flex;

  align-items: center;

  justify-content: space-between;

  padding: 0 20px;

  height: 56px;

  flex-shrink: 0;

  position: sticky;

  top: 0;

  z-index: 9;

}



.cd-header-left {

  display: flex;

  align-items: center;

  min-width: 0;

}



.cd-page-title {

  margin: 0;

  font-size: 17px;

  font-weight: 700;

  color: var(--cd-text-primary);

  letter-spacing: -0.01em;

}



.cd-header-right {

  display: flex;

  align-items: center;

  gap: 8px;

}



.cd-header-btn {
  width: 36px !important;
  height: 36px !important;
  border: none !important;
  color: #111827 !important;
  transition: var(--cd-transition-fast);
}

.cd-header-btn:hover {
  background: var(--cd-primary-bg) !important;
  color: #111827 !important;
}



/* ============================================
   Header 传输按钮
   ============================================ */
.cd-header-transfer-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.cd-transfer-btn {
  width: 36px !important;
  height: 36px !important;
  border: none !important;
  color: #111827 !important;
  transition: var(--cd-transition-fast);
  display: flex !important;
  align-items: center;
  justify-content: center;
  padding: 0 !important;
  background: transparent !important;
  cursor: pointer;
  border-radius: 10px !important;
}

.cd-transfer-btn:hover {
  background: var(--cd-primary-bg) !important;
  color: #111827 !important;
}

.cd-transfer-btn.active {
  color: #111827 !important;
}

.cd-transfer-btn-icon {
  width: 18px;
  height: 18px;
}

.cd-transfer-btn-badge {
  position: absolute;
  top: -2px;
  right: -3px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  border-radius: 99px;
  background: var(--theme-primary-gradient, linear-gradient(135deg, #4f46e5 0%, #6366f1 100%));
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  border: 1.5px solid #fff;
  box-shadow: 0 1px 4px rgba(79, 70, 229, 0.25);
  animation: badgePopIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes badgePopIn {
  from {
    transform: scale(0);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}



/* 用户信息 */

.cd-user-info {

  display: flex;

  align-items: center;

  gap: 8px;

  padding: 4px 12px 4px 4px;

  border-radius: var(--cd-radius-full);

  cursor: pointer;

  transition: var(--cd-transition-fast);

}



.cd-user-info:hover {

  background: var(--cd-primary-bg);

}



.cd-user-avatar {

  background: var(--cd-primary-gradient) !important;

  color: #fff !important;

  font-weight: 600 !important;

  font-size: 13px !important;

}

.cd-user-avatar-skeleton {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  flex-shrink: 0;
  background: linear-gradient(90deg, var(--cd-border) 25%, var(--cd-bg-soft) 50%, var(--cd-border) 75%);
  background-size: 200% 100%;
  animation: cd-avatar-shimmer 1.2s ease-in-out infinite;
}

@keyframes cd-avatar-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}



.cd-user-name {

  font-size: 14px;

  font-weight: 500;

  color: var(--cd-text-primary);

  max-width: 120px;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

}



.cd-user-arrow {

  color: var(--cd-text-placeholder) !important;

}



/* ============================================

   主内容区

   ============================================ */

.cd-main {

  --el-main-padding: 0;

  padding: 0 !important;

  flex: 1;

  min-height: 0;

  overflow: hidden;

  display: flex;

  flex-direction: column;

  animation: fadeIn 0.3s ease;

}



/* ============================================

   通知抽屉

   ============================================ */

.cd-notify-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 36px;
}



.cd-notify-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text-primary);
}

.cd-clear-warn {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 16px;
  background: #fff7ed;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
  color: #9a3412;
}

.cd-clear-warn span {
  flex: 1;
}

.cd-dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cd-dialog-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--cd-radius);
  background: var(--cd-primary-bg);
  color: var(--cd-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cd-dialog-icon.danger {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.cd-dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text-primary);
}

.cd-dialog-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--cd-text-secondary);
  line-height: 1.4;
}

.cd-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.cd-notify-header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}



.cd-notify-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cd-notify-item {
  position: relative;
  padding: 14px 16px;
  border-radius: var(--cd-radius-lg);
  border: 1px solid var(--cd-border-light);
  border-left: 4px solid transparent;
  cursor: pointer;
  transition: var(--cd-transition-fast);
  display: flex;
  gap: 12px;
  align-items: flex-start;
  background: #ffffff;
}

.cd-notify-item:hover {
  border-color: var(--cd-primary-light);
  background: var(--cd-primary-bg);
}

.cd-notify-item.unread {
  background: rgba(79, 124, 255, 0.02);
  border-color: rgba(79, 124, 255, 0.15);
  border-left: 4px solid var(--cd-primary);
}

.cd-notify-icon-box {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cd-notify-icon-box.teal {
  background: rgba(13, 148, 136, 0.08);
  color: #0d9488;
}

.cd-notify-icon-box.blue {
  background: rgba(59, 130, 246, 0.08);
  color: #3b82f6;
}

.cd-notify-icon-box.orange {
  background: rgba(249, 115, 22, 0.08);
  color: #f97316;
}

.cd-notify-icon-box.indigo {
  background: rgba(99, 102, 241, 0.08);
  color: #6366f1;
}

.cd-notify-item-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
  width: 100%;
}

.cd-notify-item-time {
  font-size: 11px;
  color: var(--cd-text-placeholder);
}

.cd-notify-content {
  flex: 1;
  min-width: 0;
}

.cd-notify-item-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--cd-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cd-notify-item-text {
  color: var(--cd-text-secondary);
  font-size: 13px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.cd-notify-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.cd-notify-action-pill {
  flex: 1;
  height: 32px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--cd-border-light);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s ease, opacity 0.15s ease, box-shadow 0.15s ease;
}

.cd-notify-action-pill:active {
  transform: scale(0.98);
}

.cd-notify-action-pill--ghost {
  background: #f8fafc;
  color: var(--cd-text-secondary);
}

.cd-notify-action-pill--ghost:hover {
  background: #f1f5f9;
}

.cd-notify-action-pill--primary {
  background: var(--cd-primary-gradient);
  color: #fff;
  border: none;
  box-shadow: 0 4px 12px rgba(1, 7, 16, 0.15);
}

.cd-notify-action-pill--primary:hover {
  opacity: 0.95;
}

.cd-notify-action-pill--lg {
  height: 44px;
  font-size: 14px;
}

/* 通知详情弹窗样式 */
.cd-notify-detail-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 10px 0;
}

.cd-notify-detail-icon-wrap {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.cd-notify-detail-icon-wrap.teal {
  background: rgba(13, 148, 136, 0.08);
  color: #0d9488;
}

.cd-notify-detail-icon-wrap.blue {
  background: rgba(59, 130, 246, 0.08);
  color: #3b82f6;
}

.cd-notify-detail-icon-wrap.orange {
  background: rgba(249, 115, 22, 0.08);
  color: #f97316;
}

.cd-notify-detail-icon-wrap.indigo {
  background: rgba(99, 102, 241, 0.08);
  color: #6366f1;
}

.cd-notify-detail-title {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 8px 0;
  color: var(--cd-text-primary);
}

.cd-notify-detail-time {
  font-size: 12px;
  color: var(--cd-text-placeholder);
  margin-bottom: 20px;
}

.cd-notify-detail-body {
  font-size: 14px;
  line-height: 1.6;
  color: var(--cd-text-secondary);
  text-align: left;
  background: var(--cd-bg-aside);
  padding: 16px;
  border-radius: var(--cd-radius-lg);
  width: 100%;
  box-sizing: border-box;
  margin-bottom: 28px;
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--cd-border-light);
}

.cd-notify-detail-actions {
  display: flex;
  gap: 12px;
  width: 100%;
  justify-content: center;
}

.cd-notify-detail-actions .cd-notify-action-pill {
  flex: 1;
  max-width: 180px;
}


/* 通知角标 - 仿传输按钮的角标做法，避免被 el-badge 默认尺寸影响 */
.cd-notify-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  cursor: pointer;
  border-radius: 50%;
  background: transparent;
  border: none;
  color: #111827;
  transition: background 0.18s ease, color 0.18s ease;
}
.cd-notify-wrap:hover {
  background: var(--cd-primary-bg);
  color: #111827;
}
.cd-notify-btn {
  width: 36px !important;
  height: 36px !important;
  background: transparent !important;
  border: none !important;
  color: inherit !important;
}
.cd-notify-dot {
  position: absolute;
  top: 1px;
  right: 1px;
  height: 15px;
  min-width: 15px;
  padding: 0 3px;
  border-radius: 10px;
  box-sizing: border-box;
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  border: 1px solid #fff;
  box-shadow: 0 1px 3px rgba(239, 68, 68, 0.25);
  animation: badgePopIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.cd-notify-item-right-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.cd-notify-delete-btn {
  padding: 0 !important;
  height: auto !important;
  font-size: 14px !important;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.cd-notify-item:hover .cd-notify-delete-btn {
  opacity: 1;
}

.cd-notify-status-row {
  margin-top: 8px;
}

.invite-status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 99px;
  font-size: 12px;
  font-weight: 600;
}

.invite-status-badge.accepted {
  background: rgba(13, 148, 136, 0.08);
  color: #0d9488;
}

.invite-status-badge.rejected {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
}

.invite-status-badge.large {
  font-size: 14px;
  padding: 8px 24px;
  border-radius: 10px;
  width: 100%;
  box-sizing: border-box;
  justify-content: center;
  margin-bottom: 16px;
}

.detail-actions-col {
  flex-direction: column;
  align-items: center;
  gap: 0;
}

.cd-storage-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed var(--cd-border-light, #e2e8f0);
  margin-top: 4px;
  padding-top: 8px;
}

.cd-storage-type {
  border-top: none !important;
  margin-top: 0 !important;
  padding-top: 0 !important;
}

.cd-storage-apply-btn {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-primary, #4f46e5);
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.cd-storage-apply-btn:hover {
  color: color-mix(in srgb, var(--theme-primary, #4f46e5) 80%, black);
  text-decoration: underline;
}

.cd-storage-apply-btn:active {
  opacity: 0.8;
}

.cd-form-label-tip {
  font-size: 11px;
  font-weight: normal;
  color: var(--cd-text-placeholder, #94a3b8);
  margin-left: 6px;
}

/* Quota Apply Dialog styling */
.cd-apply-quota-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.cd-apply-quota-dialog :deep(.el-dialog__header) {
  padding: 24px 24px 12px;
  border-bottom: none;
}

.cd-apply-quota-dialog :deep(.el-dialog__body) {
  padding: 8px 24px 24px;
}

.cd-apply-quota-dialog :deep(.el-dialog__footer) {
  padding: 16px 24px 20px;
  border-top: 1px solid var(--cd-border-light, #f1f5f9);
  background: var(--cd-bg-aside, #f8fafc);
}

.cd-quota-dialog-header-clean {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cd-quota-dialog-icon-clean {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--theme-primary-muted, rgba(79, 70, 229, 0.08));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cd-quota-dialog-title-clean {
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text-primary, #0f172a);
}

.cd-quota-hint-banner {
  display: flex;
  align-items: center;
  background: rgba(79, 70, 229, 0.04);
  border-left: 4px solid var(--theme-primary, #4f46e5);
  border-radius: 2px 8px 8px 2px;
  padding: 10px 14px;
  margin-bottom: 20px;
}

.cd-quota-hint-banner .hint-text {
  font-size: 13px;
  line-height: 1.5;
  color: var(--cd-text-secondary, #475569);
  font-weight: 500;
}

.cd-quota-fixed-value {
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid var(--cd-border-light);
  font-size: 15px;
  font-weight: 700;
  color: var(--cd-text);
}

.cd-form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  text-align: left;
}

.cd-form-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--cd-text-primary, #0f172a);
}

.cd-input-suffix-text {
  font-size: 12px;
  font-weight: 700;
  color: var(--cd-text-placeholder, #94a3b8);
  padding-right: 4px;
}

.cd-quota-cancel-btn {
  border-radius: 99px !important;
  font-weight: 600;
  border-color: var(--cd-border, #cbd5e1) !important;
}

.cd-quota-submit-btn {
  border-radius: 99px !important;
  font-weight: 700;
  background: var(--cd-primary-gradient, linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)) !important;
  border: none !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2) !important;
}

.cd-quota-submit-btn:hover {
  opacity: 0.95;
}

</style>
