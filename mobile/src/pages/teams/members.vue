<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { request, fileApiUrl } from '@/api/http'
import MobileHeader from '@/components/MobileHeader.vue'
import MobilePromptDialog from '@/components/MobilePromptDialog.vue'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'
import { teamAvatarVersions } from '@/utils/teamAvatar'

const auth = useAuthStore()

interface TeamMember {
  userId: number
  username?: string
  nickname?: string
  avatar?: string
  hasAvatar?: boolean
  role: string
  joinTime: string
}

const spaceId = ref(0)
const spaceName = ref('')
const spaceAvatar = ref('')
const myRole = ref('')
const members = ref<TeamMember[]>([])
const membersLoading = ref(false)
const inviteVisible = ref(false)
const inviting = ref(false)
const inviteConfirmVisible = ref(false)
const inviteSuccessVisible = ref(false)
const pendingInviteUsername = ref('')
const avatarBroken = ref<Record<number, boolean>>({})
const removeVisible = ref(false)
const removeTarget = ref<TeamMember | null>(null)

const canInvite = computed(() => myRole.value === 'OWNER' || myRole.value === 'ADMIN')

const teamInitial = computed(() => (spaceName.value.charAt(0) || 'T').toUpperCase())

function getTeamAvatarUrl(teamId: number) {
  const v = teamAvatarVersions.value[teamId] || 0
  const base = fileApiUrl(`/api/teams/${teamId}/avatar`)
  return v ? `${base}&v=${v}` : base
}

const teamAvatarStyle = computed(() => {
  const gradients = [
    'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)',
    'linear-gradient(135deg, #0ea5e9 0%, #2563eb 100%)',
    'linear-gradient(135deg, #10b981 0%, #059669 100%)',
    'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
    'linear-gradient(135deg, #ec4899 0%, #db2777 100%)',
    'linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%)'
  ]
  const shadows = [
    'rgba(79, 70, 229, 0.22)',
    'rgba(14, 165, 233, 0.22)',
    'rgba(16, 185, 129, 0.22)',
    'rgba(245, 158, 11, 0.22)',
    'rgba(236, 72, 153, 0.22)',
    'rgba(139, 92, 246, 0.22)'
  ]
  const idx = spaceId.value % gradients.length
  return {
    background: gradients[idx],
    boxShadow: `0 8rpx 20rpx ${shadows[idx]}`
  }
})

onLoad((query) => {
  spaceId.value = Number(query?.spaceId || 0)
  spaceName.value = decodeURIComponent(query?.name || '团队空间')
  spaceAvatar.value = decodeURIComponent(query?.avatar || '')
  myRole.value = query?.myRole || ''
})

onShow(() => {
  if (!auth.requireLogin()) return
  auth.fetchProfile().catch(() => {})
  syncSpaceMeta()
  loadMembers()
})

async function syncSpaceMeta() {
  if (!spaceId.value) return
  try {
    const space = await request<{ name: string; avatar?: string }>({ url: `/api/teams/${spaceId.value}` })
    spaceName.value = space.name
    spaceAvatar.value = space.avatar || ''
  } catch {
    /* handled */
  }
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

function memberDisplayName(member: TeamMember) {
  return member.nickname || member.username || `用户${member.userId}`
}

function memberInitial(member: TeamMember) {
  return memberDisplayName(member).charAt(0).toUpperCase()
}

function memberAvatarSrc(member: TeamMember) {
  if (avatarBroken.value[member.userId]) return ''
  const hasAvatar = member.hasAvatar ?? !!member.avatar
  if (!hasAvatar) return ''
  if (member.username === auth.username && auth.hasAvatar) {
    return auth.avatarSrc
  }
  return fileApiUrl(`/api/teams/${spaceId.value}/members/${member.userId}/avatar`)
}

function onMemberAvatarError(userId: number) {
  avatarBroken.value[userId] = true
}

async function loadMembers() {
  membersLoading.value = true
  avatarBroken.value = {}
  try {
    members.value = await request<TeamMember[]>({
      url: `/api/teams/${spaceId.value}/members`
    })
    const me = members.value.find((m) => m.username === auth.username)
    if (me) {
      myRole.value = me.role
    }
  } catch {
    /* handled */
  } finally {
    membersLoading.value = false
  }
}

async function submitInvite(username: string) {
  const name = username.trim()
  if (!name) {
    uni.showToast({ title: '请输入用户名', icon: 'none' })
    return
  }
  pendingInviteUsername.value = name
  inviteVisible.value = false
  inviteConfirmVisible.value = true
}

async function confirmSubmitInvite() {
  if (inviting.value) return
  inviting.value = true
  try {
    await request({
      url: `/api/teams/${spaceId.value}/members`,
      method: 'POST',
      data: { username: pendingInviteUsername.value, role: 'MEMBER' }
    })
    inviteConfirmVisible.value = false
    inviteSuccessVisible.value = true
    await loadMembers()
  } catch {
    /* handled */
  } finally {
    inviting.value = false
  }
}

function removeMember(member: TeamMember) {
  removeTarget.value = member
  removeVisible.value = true
}

async function confirmRemove() {
  const member = removeTarget.value
  if (!member) return
  try {
    await request({
      url: `/api/teams/${spaceId.value}/members/${member.userId}`,
      method: 'DELETE'
    })
    uni.showToast({ title: '已移除', icon: 'success' })
    await loadMembers()
  } catch {
    /* handled */
  }
}

</script>

<template>
  <view class="page">
    <MobileHeader
      title="成员管理"
      :subtitle="`${members.length} 人`"
      gradient
    >
      <template #right>
        <view v-if="canInvite" class="invite-btn cd-pressable" @click="inviteVisible = true">
          <u-icon name="plus" size="14" color="#000000" bold />
          <text class="invite-btn-text">邀请</text>
        </view>
      </template>
      <template #extra>
        <view class="team-hero">
          <view class="team-avatar" :style="spaceAvatar ? {} : teamAvatarStyle">
            <image
              v-if="spaceAvatar"
              :src="getTeamAvatarUrl(spaceId)"
              class="team-avatar-img"
              mode="aspectFill"
            />
            <text v-else class="team-avatar-text">{{ teamInitial }}</text>
          </view>
          <view class="team-hero-text">
            <text class="team-hero-name">{{ spaceName }}</text>
            <text class="team-hero-desc">成员管理 · 共 {{ members.length }} 位成员</text>
          </view>
        </view>
      </template>
    </MobileHeader>

    <scroll-view scroll-y class="member-scroll">
      <view v-if="membersLoading" class="state-box">
        <u-loading-icon text="正在加载成员列表..." color="var(--cd-primary)" size="18" />
      </view>
      <view v-else class="member-list">
        <view v-for="member in members" :key="member.userId" class="member-item">
          <view class="member-info">
            <view class="member-avatar">
              <image
                v-if="memberAvatarSrc(member)"
                :src="memberAvatarSrc(member)"
                class="member-avatar-img"
                mode="aspectFill"
                @error="onMemberAvatarError(member.userId)"
              />
              <text v-else class="member-avatar-text">{{ memberInitial(member) }}</text>
            </view>
            <view class="member-meta">
              <text class="member-name">{{ memberDisplayName(member) }}</text>
              <text class="member-time">加入时间：{{ new Date(member.joinTime).toLocaleDateString() }}</text>
            </view>
          </view>
          <view class="member-item-right">
            <view
              class="member-role-badge"
              :style="{ background: roleColor(member.role) + '12', color: roleColor(member.role), border: '1rpx solid ' + roleColor(member.role) + '2a' }"
            >
              <text>{{ roleLabel(member.role) }}</text>
            </view>
            <view
              v-if="member.role !== 'OWNER' && canInvite && member.username !== auth.username"
              class="remove-action cd-pressable"
              @click="removeMember(member)"
            >
              <text class="remove-action-text">移除</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <MobilePromptDialog
      v-model:show="inviteVisible"
      title="邀请成员"
      placeholder="请输入被邀请人的用户名"
      confirm-text="邀请"
      @confirm="submitInvite"
    >
      <template #desc>输入对方登录用户名即可邀请加入团队</template>
    </MobilePromptDialog>

    <MobileConfirmDialog
      v-model:show="removeVisible"
      title="移除成员"
      :message="removeTarget ? `确定将「${removeTarget.nickname || removeTarget.username}」移出团队吗？` : ''"
      confirm-text="移除"
      danger
      @confirm="confirmRemove"
    />

    <MobileConfirmDialog
      v-model:show="inviteConfirmVisible"
      title="发送邀请"
      :message="`确定向用户「${pendingInviteUsername}」发送团队邀请吗？`"
      confirm-text="确定发送"
      tone="info"
      @confirm="confirmSubmitInvite"
    />

    <MobileConfirmDialog
      v-model:show="inviteSuccessVisible"
      title="邀请已发送"
      :message="`已向「${pendingInviteUsername}」发出邀请。对方在消息中心接受后，即会加入团队。`"
      confirm-text="好的"
      alert-only
      tone="success"
    />
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding-bottom: calc(env(safe-area-inset-bottom) + 20rpx);
  background: var(--cd-bg);
}

.invite-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 20rpx;
  border-radius: var(--cd-radius-full);
  background: var(--cd-border);
  transition: all var(--cd-transition-fast);
}

.invite-btn:active {
  transform: scale(0.95);
  opacity: 0.85;
}

.invite-btn-text {
  font-size: 22rpx;
  font-weight: 700;
  color: var(--cd-text);
}

.team-hero {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 8rpx 4rpx 0;
}

.team-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.team-avatar-img {
  width: 100%;
  height: 100%;
}

.team-avatar-text {
  font-size: 36rpx;
  font-weight: 800;
  color: #fff;
}

.team-hero-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.team-hero-name {
  font-size: 40rpx;
  font-weight: 800;
  color: var(--cd-text);
  letter-spacing: -0.5rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-hero-desc {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--cd-text-secondary);
}

.member-scroll {
  height: calc(100vh - 420rpx);
}

.state-box {
  padding: 120rpx 0;
  display: flex;
  justify-content: center;
}

.member-list {
  padding: 16rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.member-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 20rpx;
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-lg);
  border: 1rpx solid var(--cd-border-light);
  box-shadow: var(--cd-shadow-card);
}

.member-info {
  display: flex;
  align-items: center;
  gap: 20rpx;
  flex: 1;
  min-width: 0;
}

.member-avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
  overflow: hidden;
  box-shadow: 0 4rpx 12rpx rgba(15, 23, 42, 0.12);
}

.member-avatar-img {
  width: 100%;
  height: 100%;
}

.member-avatar-text {
  font-size: 28rpx;
  font-weight: 800;
  color: #fff;
}

.member-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.member-name {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--cd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-time {
  font-size: 20rpx;
  color: var(--cd-text-muted);
}

.member-item-right {
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex-shrink: 0;
}

.member-role-badge {
  padding: 4rpx 14rpx;
  border-radius: var(--cd-radius-full);
  font-size: 18rpx;
  font-weight: 700;
}

.remove-action {
  padding: 6rpx 18rpx;
  border-radius: var(--cd-radius-xs);
  background: var(--cd-danger-bg);
  border: 1rpx solid rgba(239, 68, 68, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--cd-transition-fast);
}

.remove-action:active {
  transform: scale(0.9);
}

.remove-action-text {
  font-size: 20rpx;
  font-weight: 700;
  color: var(--cd-danger);
}
</style>
