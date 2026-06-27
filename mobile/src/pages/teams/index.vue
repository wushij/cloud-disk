<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { request, fileApiUrl, uploadFile } from '@/api/http'
import MobileTabBar from '@/components/MobileTabBar.vue'
import MobileHeader from '@/components/MobileHeader.vue'
import MobilePromptDialog from '@/components/MobilePromptDialog.vue'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import { globalTeamList } from '@/utils/sharedState'
import { bumpTeamAvatarVersion, teamAvatarVersions, getTeamAvatarVersion } from '@/utils/teamAvatar'
import CachedEntityAvatar from '@/components/CachedEntityAvatar.vue'
import MemberCachedAvatar from '@/components/MemberCachedAvatar.vue'
import { cacheEntityAvatarFromPath, teamAvatarCacheKey } from '@/utils/entityAvatarCache'

const auth = useAuthStore()

interface TeamSpace {
  id: number
  name: string
  ownerId: number
  rootFolderId: number
  myRole: string
  memberCount: number
  createdAt: string
  avatar?: string
}

const teams = globalTeamList
const loading = ref(false)
const createVisible = ref(false)
const creating = ref(false)

onShow(async () => {
  uni.hideTabBar({ animation: false }).catch(() => {})
  if (!auth.requireLogin()) return
  await loadTeams()
})

async function loadTeams() {
  loading.value = true
  try {
    teams.value = (await request<TeamSpace[]>({ url: '/api/teams' })) ?? []
  } catch {
    /* handled */
  } finally {
    loading.value = false
  }
}

const gradients = [
  'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)', // 靛蓝
  'linear-gradient(135deg, #0ea5e9 0%, #2563eb 100%)', // 蔚蓝
  'linear-gradient(135deg, #10b981 0%, #059669 100%)', // 翡翠
  'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)', // 琥珀
  'linear-gradient(135deg, #ec4899 0%, #db2777 100%)', // 玫瑰
  'linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%)'  // 罗兰
]

const shadowColors = [
  'rgba(79, 70, 229, 0.22)',
  'rgba(14, 165, 233, 0.22)',
  'rgba(16, 185, 129, 0.22)',
  'rgba(245, 158, 11, 0.22)',
  'rgba(236, 72, 153, 0.22)',
  'rgba(139, 92, 246, 0.22)'
]

function getAvatarStyle(teamId: number) {
  const idx = teamId % gradients.length
  return {
    background: gradients[idx],
    boxShadow: `0 8rpx 20rpx ${shadowColors[idx]}`
  }
}

function getTeamAvatarUrl(teamId: number, avatarPath?: string) {
  if (!avatarPath) return ''
  const v = teamAvatarVersions.value[teamId] || 0
  const base = fileApiUrl(`/api/teams/${teamId}/avatar`)
  return v ? `${base}&v=${v}` : base
}

function changeTeamAvatar(team: TeamSpace) {
  if (team.myRole !== 'OWNER' && team.myRole !== 'ADMIN') return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const tempFilePath = res.tempFilePaths[0]
      uni.showLoading({ title: '上传中...' })
      try {
        const data = await uploadFile({
          url: `/api/teams/${team.id}/avatar`,
          filePath: tempFilePath,
          name: 'file'
        }) as { avatar: string }
        bumpTeamAvatarVersion(team.id)
        void cacheEntityAvatarFromPath(
          teamAvatarCacheKey(team.id),
          getTeamAvatarVersion(team.id),
          tempFilePath
        ).catch(() => {})
        const idx = teams.value.findIndex((t) => t.id === team.id)
        if (idx >= 0) {
          teams.value[idx] = { ...teams.value[idx], avatar: data.avatar }
        }
        uni.showToast({ title: '修改成功', icon: 'success' })
      } catch (err) {
        uni.showToast({ title: '上传失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

function enterTeam(team: TeamSpace) {
  uni.navigateTo({
    url: `/pages/teams/files?spaceId=${team.id}&name=${encodeURIComponent(team.name)}&rootFolderId=${team.rootFolderId}&myRole=${team.myRole}&avatar=${encodeURIComponent(team.avatar || '')}`
  })
}

function roleLabel(role: string) {
  switch (role) {
    case 'OWNER': return '创建者'
    case 'ADMIN': return '管理员'
    case 'VIEWER': return '只读成员'
    default: return '成员'
  }
}

function roleColor(role: string) {
  switch (role) {
    case 'OWNER': return '#f59e0b'
    case 'ADMIN': return '#22c55e'
    case 'VIEWER': return '#6366f1'
    default: return '#94a3b8'
  }
}

function createTeam() {
  createVisible.value = true
}

async function submitCreateTeam(name: string) {
  if (!name) {
    uni.showToast({ title: '请输入团队名称', icon: 'none' })
    return
  }
  if (creating.value) return
  creating.value = true
  try {
    await request({ url: '/api/teams', method: 'POST', data: { name } })
    createVisible.value = false
    uni.showToast({ title: '创建成功', icon: 'success' })
    await loadTeams()
  } catch {
    /* handled */
  } finally {
    creating.value = false
  }
}

const actionVisible = ref(false)
const selectedTeam = ref<TeamSpace | null>(null)
const confirmVisible = ref(false)
const confirmMode = ref<'dissolve' | 'leave'>('dissolve')
const renameVisible = ref(false)
const renaming = ref(false)

const confirmTitle = computed(() => (confirmMode.value === 'dissolve' ? '解散团队' : '退出团队'))
const confirmMessage = computed(() => {
  const team = selectedTeam.value
  if (!team) return ''
  if (confirmMode.value === 'dissolve') {
    return `确认解散团队「${team.name}」吗？所有成员将被移除，团队文件将被移入回收站！`
  }
  return `确认退出团队「${team.name}」吗？退出后您将无法再访问其共享文件。`
})

const actionList = computed(() => {
  if (!selectedTeam.value) return []
  const list: { name: string; color?: string }[] = [{ name: '成员管理' }]
  if (selectedTeam.value.myRole === 'OWNER' || selectedTeam.value.myRole === 'ADMIN') {
    list.push({ name: '更换团队头像' })
    list.push({ name: '重命名团队' })
  }
  if (selectedTeam.value.myRole === 'OWNER') {
    list.push({ name: '解散团队', color: '#ef4444' })
  } else {
    list.push({ name: '退出团队', color: '#ef4444' })
  }
  return list
})

function showTeamActions(team: TeamSpace) {
  selectedTeam.value = team
  actionVisible.value = true
}

function onActionSelect(item: { name: string }) {
  const team = selectedTeam.value
  actionVisible.value = false
  if (!team) return
  if (item.name === '成员管理') {
    uni.navigateTo({
      url: `/pages/teams/members?spaceId=${team.id}&name=${encodeURIComponent(team.name)}&myRole=${team.myRole}&avatar=${encodeURIComponent(team.avatar || '')}`
    })
    return
  }
  if (item.name === '更换团队头像') {
    changeTeamAvatar(team)
    return
  }
  if (item.name === '重命名团队') {
    renameVisible.value = true
    return
  }
  if (item.name === '解散团队') {
    confirmMode.value = 'dissolve'
    confirmVisible.value = true
  } else if (item.name === '退出团队') {
    confirmMode.value = 'leave'
    confirmVisible.value = true
  }
}

async function submitRenameTeam(name: string) {
  const team = selectedTeam.value
  const trimmed = name.trim()
  if (!team || !trimmed) {
    uni.showToast({ title: '请输入团队名称', icon: 'none' })
    return
  }
  if (renaming.value) return
  renaming.value = true
  try {
    await request({ url: `/api/teams/${team.id}`, method: 'PUT', data: { name: trimmed } })
    renameVisible.value = false
    uni.showToast({ title: '已重命名', icon: 'success' })
    await loadTeams()
  } catch {
    /* handled */
  } finally {
    renaming.value = false
  }
}

async function onConfirmAction() {
  const team = selectedTeam.value
  if (!team) return
  try {
    if (confirmMode.value === 'dissolve') {
      await request({ url: `/api/teams/${team.id}`, method: 'DELETE' })
      uni.showToast({ title: '已解散团队', icon: 'success' })
      await loadTeams()
      return
    }
    await request({ url: `/api/teams/${team.id}/leave`, method: 'POST' })
    uni.showToast({ title: '已退出团队', icon: 'success' })
    await loadTeams()
  } catch {
    /* handled */
  }
}

let touchTimer: ReturnType<typeof setTimeout> | null = null
let startX = 0
let startY = 0
let isLongPress = false
let longPressActiveTeamId: number | null = null

function onTeamTouchStart(e: TouchEvent, team: TeamSpace) {
  if (e.touches.length !== 1) return
  const touch = e.touches[0]
  startX = touch.clientX
  startY = touch.clientY
  isLongPress = false
  longPressActiveTeamId = team.id

  if (touchTimer) clearTimeout(touchTimer)
  touchTimer = setTimeout(() => {
    isLongPress = true
    showTeamActions(team)
  }, 600)
}

function onTeamTouchMove(e: TouchEvent) {
  if (!touchTimer) return
  const touch = e.touches[0]
  const deltaX = Math.abs(touch.clientX - startX)
  const deltaY = Math.abs(touch.clientY - startY)
  if (deltaX > 10 || deltaY > 10) {
    clearTimeout(touchTimer)
    touchTimer = null
  }
}

function onTeamTouchEnd() {
  if (touchTimer) {
    clearTimeout(touchTimer)
    touchTimer = null
  }
}

function onTeamTouchCancel() {
  if (touchTimer) {
    clearTimeout(touchTimer)
    touchTimer = null
  }
}

function onTeamClick(team: TeamSpace) {
  if (isLongPress && longPressActiveTeamId === team.id) {
    isLongPress = false
    longPressActiveTeamId = null
    return
  }
  enterTeam(team)
}

onUnmounted(() => {
  if (touchTimer) clearTimeout(touchTimer)
})
</script>

<template>
  <view class="page">
    <MobileHeader title="团队空间" :subtitle="`${teams.length} 个团队`" gradient icon-type="team">
      <template #right>
        <view class="header-actions">
          <view class="add-btn cd-pressable" @click="createTeam">
            <u-icon name="plus" size="16" color="#000000" bold />
          </view>
        </view>
      </template>
    </MobileHeader>

    <scroll-view scroll-y class="content-scroll">
      <view v-if="loading && !teams.length" class="state-box">
        <u-loading-icon text="加载中" color="var(--cd-primary)" />
      </view>
      <EmptyState
        v-else-if="!teams.length"
        icon="account-fill"
        title="还没有团队"
        description="创建团队空间，与成员共享文件"
      >
        <template #action>
          <view class="create-btn cd-pressable" @click="createTeam">
            <u-icon name="plus" size="14" color="#fff" />
            <text class="create-btn-text">创建团队</text>
          </view>
        </template>
      </EmptyState>
      <view v-else class="team-list">
        <view
          v-for="team in teams"
          :key="team.id"
          class="team-card cd-pressable"
          @touchstart="onTeamTouchStart($event, team)"
          @touchmove="onTeamTouchMove"
          @touchend="onTeamTouchEnd"
          @touchcancel="onTeamTouchCancel"
          @click="onTeamClick(team)"
        >
          <view class="team-card-left">
            <view class="team-avatar" :style="team.avatar ? {} : getAvatarStyle(team.id)">
              <CachedEntityAvatar
                v-if="team.avatar"
                :cache-key="teamAvatarCacheKey(team.id)"
                :src="getTeamAvatarUrl(team.id, team.avatar)"
                :version="getTeamAvatarVersion(team.id)"
              />
              <text class="team-avatar-text" v-else>{{ team.name.charAt(0) }}</text>
            </view>
          </view>
          <view class="team-card-info">
            <text class="team-name">{{ team.name }}</text>
            <view class="team-meta">
              <view class="team-role-badge" :style="{ background: roleColor(team.myRole) + '12', color: roleColor(team.myRole), border: '1rpx solid ' + roleColor(team.myRole) + '2a' }">
                <text>{{ roleLabel(team.myRole) }}</text>
              </view>
              <text class="team-members">{{ team.memberCount }} 位成员</text>
            </view>
          </view>
          <view class="action-trigger" @click.stop="showTeamActions(team)">
            <u-icon name="more-dot-fill" color="#94a3b8" size="18" />
          </view>
        </view>
      </view>
    </scroll-view>

    <u-action-sheet
      :show="actionVisible"
      :actions="actionList"
      cancel-text="取消"
      round="16"
      @close="actionVisible = false"
      @select="onActionSelect"
    />

    <MobilePromptDialog
      v-model:show="createVisible"
      title="创建团队空间"
      placeholder="输入团队名称"
      confirm-text="创建"
      @confirm="submitCreateTeam"
    >
      <template #desc>与成员共享和管理文件</template>
    </MobilePromptDialog>

    <MobileConfirmDialog
      v-model:show="confirmVisible"
      :title="confirmTitle"
      :message="confirmMessage"
      :confirm-text="confirmMode === 'dissolve' ? '解散' : '退出'"
      danger
      @confirm="onConfirmAction"
    />

    <MobilePromptDialog
      v-model:show="renameVisible"
      title="重命名团队"
      placeholder="输入新的团队名称"
      confirm-text="保存"
      :initial-value="selectedTeam?.name || ''"
      @confirm="submitRenameTeam"
    />

    <MobileTabBar active="teams" />
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding-bottom: calc(var(--cd-tab-height) + env(safe-area-inset-bottom) + 20rpx);
  background: var(--cd-bg);
}

.add-btn {
  width: 44rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  transition: opacity var(--cd-transition-fast);

  &:active {
    opacity: 0.55;
  }
}

.header-actions {
  display: flex;
  align-items: center;
}

.content-scroll {
  height: calc(100vh - 260rpx);
}

.state-box {
  padding: 120rpx 0;
  display: flex;
  justify-content: center;
}

.team-list {
  padding: 16rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.team-card {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 28rpx 24rpx;
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-lg);
  box-shadow: var(--cd-shadow-card);
  border: 1rpx solid var(--cd-border-light);
  transition: all var(--cd-transition-bounce);
}

.team-card:active {
  transform: scale(0.985);
  box-shadow: var(--cd-shadow-sm);
  border-color: rgba(1, 7, 16, 0.08);
}

.team-avatar {
  width: 82rpx;
  height: 82rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.team-avatar-img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
}

.team-avatar-text {
  font-size: 34rpx;
  font-weight: 800;
  color: #fff;
}

.team-card-info {
  flex: 1;
  min-width: 0;
}

.team-name {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--cd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-meta {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 6rpx;
}

.team-role-badge {
  padding: 4rpx 14rpx;
  border-radius: var(--cd-radius-full);
  font-size: 18rpx;
  font-weight: 700;
  letter-spacing: 0.5rpx;
}

.team-members {
  font-size: 22rpx;
  color: var(--cd-text-muted);
}

.action-trigger {
  padding: 10rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.create-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 16rpx 36rpx;
  background: var(--cd-primary-gradient);
  border-radius: var(--cd-radius-full);
  margin-top: 10rpx;
  box-shadow: 0 8rpx 20rpx rgba(1, 7, 16, 0.16);
  transition: all var(--cd-transition-fast);
}

.create-btn:active {
  transform: scale(0.95);
  box-shadow: 0 4rpx 10rpx rgba(1, 7, 16, 0.1);
}

.create-btn-text {
  font-size: 24rpx;
  font-weight: 600;
  color: #fff;
}
</style>
