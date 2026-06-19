<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { request } from '@/api/http'
import MobileTabBar from '@/components/MobileTabBar.vue'
import MobileHeader from '@/components/MobileHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

const auth = useAuthStore()

interface TeamSpace {
  id: number
  name: string
  ownerId: number
  rootFolderId: number
  myRole: string
  memberCount: number
  createdAt: string
}

const teams = ref<TeamSpace[]>([])
const loading = ref(false)

onShow(async () => {
  if (!auth.requireLogin()) return
  await loadTeams()
})

async function loadTeams() {
  loading.value = true
  try {
    teams.value = await request<TeamSpace[]>({ url: '/api/teams' })
  } catch {
    /* handled */
  } finally {
    loading.value = false
  }
}

function enterTeam(team: TeamSpace) {
  uni.navigateTo({
    url: `/pages/teams/files?spaceId=${team.id}&name=${encodeURIComponent(team.name)}&rootFolderId=${team.rootFolderId}`
  })
}

function roleLabel(role: string) {
  switch (role) {
    case 'OWNER': return '创建者'
    case 'ADMIN': return '管理员'
    default: return '成员'
  }
}

function roleColor(role: string) {
  switch (role) {
    case 'OWNER': return '#f59e0b'
    case 'ADMIN': return '#22c55e'
    default: return '#94a3b8'
  }
}

async function createTeam() {
  uni.showModal({
    title: '创建团队空间',
    editable: true,
    placeholderText: '输入团队名称',
    success: async (res) => {
      if (!res.confirm || !res.content?.trim()) return
      try {
        await request({ url: '/api/teams', method: 'POST', data: { name: res.content.trim() } })
        uni.showToast({ title: '创建成功', icon: 'success' })
        await loadTeams()
      } catch {
        /* handled */
      }
    }
  })
}

const actionVisible = ref(false)
const selectedTeam = ref<TeamSpace | null>(null)

const actionList = computed(() => {
  if (!selectedTeam.value) return []
  if (selectedTeam.value.myRole === 'OWNER') {
    return [{ name: '解散团队', color: '#ef4444' }]
  } else {
    return [{ name: '退出团队', color: '#ef4444' }]
  }
})

function showTeamActions(team: TeamSpace) {
  selectedTeam.value = team
  actionVisible.value = true
}

async function onActionSelect(item: { name: string }) {
  const team = selectedTeam.value
  actionVisible.value = false
  if (!team) return
  
  if (item.name === '解散团队') {
    uni.showModal({
      title: '解散团队',
      content: `确认解散团队「${team.name}」吗？所有成员将被移除，团队文件将被移入回收站！`,
      success: async (res) => {
        if (!res.confirm) return
        try {
          await request({ url: `/api/teams/${team.id}`, method: 'DELETE' })
          uni.showToast({ title: '已解散团队', icon: 'success' })
          loadTeams()
        } catch {
          /* handled */
        }
      }
    })
  } else if (item.name === '退出团队') {
    uni.showModal({
      title: '退出团队',
      content: `确认退出团队「${team.name}」吗？退出后您将无法再访问其共享文件。`,
      success: async (res) => {
        if (!res.confirm) return
        try {
          await request({ url: `/api/teams/${team.id}/leave`, method: 'POST' })
          uni.showToast({ title: '已退出团队', icon: 'success' })
          loadTeams()
        } catch {
          /* handled */
        }
      }
    })
  }
}
</script>

<template>
  <view class="page">
    <MobileHeader title="团队空间" :subtitle="`${teams.length} 个团队`" gradient icon-type="team">
      <template #right>
        <view class="add-btn cd-pressable" @click="createTeam">
          <u-icon name="plus" size="20" color="#fff" />
        </view>
      </template>
    </MobileHeader>

    <scroll-view scroll-y class="content-scroll">
      <view v-if="loading" class="state-box">
        <u-loading-icon text="加载中" color="var(--cd-primary)" />
      </view>
      <EmptyState
        v-else-if="!teams.length"
        icon="account-fill"
        title="还没有团队"
        description="创建团队空间，与成员共享文件"
      />
      <view v-else class="team-list">
        <view
          v-for="team in teams"
          :key="team.id"
          class="team-card cd-pressable"
          @click="enterTeam(team)"
          @longpress="showTeamActions(team)"
        >
          <view class="team-card-left">
            <view class="team-avatar">
              <text class="team-avatar-text">{{ team.name.charAt(0) }}</text>
            </view>
          </view>
          <view class="team-card-info">
            <text class="team-name">{{ team.name }}</text>
            <view class="team-meta">
              <view class="team-role-badge" :style="{ background: roleColor(team.myRole) + '1a', color: roleColor(team.myRole) }">
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

    <MobileTabBar active="disk" />
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding-bottom: calc(var(--cd-tab-height) + env(safe-area-inset-bottom) + 20rpx);
  background: var(--cd-bg);
}

.add-btn {
  width: 60rpx;
  height: 60rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10rpx);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--cd-transition-fast);
  &:active {
    background: rgba(255, 255, 255, 0.32);
    transform: scale(0.9);
  }
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
  padding: 24rpx 22rpx;
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-lg);
  box-shadow: var(--cd-shadow-card);
  border: 1rpx solid var(--cd-border-light);
  transition: all var(--cd-transition-bounce);
}

.team-card:active {
  transform: scale(0.98);
  box-shadow: var(--cd-shadow);
}

.team-avatar {
  width: 82rpx;
  height: 82rpx;
  border-radius: 22rpx;
  background: var(--cd-primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.12);
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
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  font-size: 18rpx;
  font-weight: 600;
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
</style>
