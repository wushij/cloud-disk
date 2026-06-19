<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import TeamSpaceIcon from '@/components/icons/TeamSpaceIcon.vue'
import http, { TOKEN_KEY } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/PageHeader.vue'
import FileGridView from '@/components/FileGridView.vue'
import PdfPreview from '@/components/PdfPreview.vue'
import VideoPreview from '@/components/VideoPreview.vue'
import type { FileItem } from '@/stores/file'
import { useTransferStore, promptCreateFolder } from '@/stores/transfer'
import { connectUploadWs, disconnectUploadWs } from '@/utils/ws'

const auth = useAuthStore()
const transferStore = useTransferStore()

interface TeamSpace {
  id: number
  name: string
  ownerId: number
  rootFolderId: number
  myRole: string
  memberCount: number
  createdAt: string
}

interface TeamMember {
  userId: number
  username?: string
  nickname?: string
  avatar?: string
  hasAvatar?: boolean
  role: string
  joinTime: string
}

const spaces = ref<TeamSpace[]>([])
const currentSpace = ref<TeamSpace | null>(null)
const members = ref<TeamMember[]>([])
const files = ref<FileItem[]>([])
const breadcrumb = ref<{ id: number; name: string }[]>([])
const currentFolderId = ref<number>(0)
const loading = ref(false)
const showMembers = ref(false)
const membersLoading = ref(false)
const avatarBroken = ref<Record<number, boolean>>({})
/** 从团队列表直接进入成员管理时使用，避免误切到文件详情页 */
const memberContextSpace = ref<TeamSpace | null>(null)

const membersContext = computed(() => memberContextSpace.value ?? currentSpace.value)

const canManageMembers = computed(
  () => membersContext.value && (membersContext.value.myRole === 'OWNER' || membersContext.value.myRole === 'ADMIN')
)
const createName = ref('')
const creating = ref(false)
const inviteVisible = ref(false)
const inviteUsername = ref('')
const inviting = ref(false)
const renameVisible = ref(false)
const renameName = ref('')
const renameTarget = ref<TeamSpace | null>(null)
const renaming = ref(false)

const fileInput = ref<HTMLInputElement | null>(null)
const folderInput = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)
const previewVisible = ref(false)
const previewUrl = ref('')
const previewType = ref('')
const previewName = ref('')

const teamGradients = [
  'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)',
  'linear-gradient(135deg, #0ea5e9 0%, #2563eb 100%)',
  'linear-gradient(135deg, #10b981 0%, #059669 100%)',
  'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
  'linear-gradient(135deg, #ec4899 0%, #db2777 100%)',
  'linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%)'
]

const gridFiles = computed(() =>
  files.value.map((row) => ({
    ...row,
    previewable:
      row.type === 'file' &&
      !!(row.mimeType?.startsWith('image/') || row.mimeType?.startsWith('video/') || row.mimeType?.includes('pdf'))
  }))
)

const createVisible = ref(false)

const isImage = computed(() => previewType.value.startsWith('image/'))
const isVideo = computed(() => previewType.value.startsWith('video/'))
const isPdf = computed(() => previewType.value.includes('pdf'))

function tokenParam() {
  return encodeURIComponent(localStorage.getItem(TOKEN_KEY) || '')
}

function teamAvatarStyle(teamId: number) {
  return { background: teamGradients[teamId % teamGradients.length] }
}

function roleLabel(role: string) {
  switch (role) {
    case 'OWNER':
      return '创建者'
    case 'ADMIN':
      return '管理员'
    default:
      return '成员'
  }
}

function roleColor(role: string) {
  switch (role) {
    case 'OWNER':
      return '#f59e0b'
    case 'ADMIN':
      return '#22c55e'
    default:
      return '#94a3b8'
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
  if (member.username === auth.username && auth.avatarSrc) return auth.avatarSrc
  const hasAvatar = member.hasAvatar ?? !!member.avatar
  if (!hasAvatar) return ''
  const token = localStorage.getItem(TOKEN_KEY)
  const ctx = membersContext.value
  if (!token || !ctx) return ''
  return `/api/teams/${ctx.id}/members/${member.userId}/avatar?access_token=${encodeURIComponent(token)}`
}

function onMemberAvatarError(userId: number) {
  avatarBroken.value[userId] = true
}

async function loadSpaces() {
  loading.value = true
  try {
    const { data } = await http.get('/api/teams')
    spaces.value = data
  } catch {
    /* global toast */
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  createName.value = ''
  createVisible.value = true
}

async function submitCreate() {
  const name = createName.value.trim()
  if (!name) {
    ElMessage.warning('请输入团队名称')
    return
  }
  if (creating.value) return
  creating.value = true
  try {
    await http.post('/api/teams', { name })
    createVisible.value = false
    ElMessage.success('创建成功')
    await loadSpaces()
  } catch {
    /* global toast */
  } finally {
    creating.value = false
  }
}

function openRenameDialog(team?: TeamSpace) {
  const target = team || currentSpace.value
  if (!target) return
  renameTarget.value = target
  renameName.value = target.name
  renameVisible.value = true
}

async function submitRename() {
  const target = renameTarget.value
  const name = renameName.value.trim()
  if (!target || !name) {
    ElMessage.warning('请输入团队名称')
    return
  }
  if (renaming.value) return
  renaming.value = true
  try {
    const { data } = await http.put(`/api/teams/${target.id}`, { name })
    renameVisible.value = false
    ElMessage.success('已重命名')
    applyTeamNameUpdate(target.id, data.name)
    if (currentSpace.value?.id === target.id) {
      currentSpace.value = { ...currentSpace.value, name: data.name }
    }
  } catch {
    /* global toast */
  } finally {
    renaming.value = false
  }
}

async function syncSpaceMeta() {
  if (!currentSpace.value) return
  try {
    const { data } = await http.get(`/api/teams/${currentSpace.value.id}`)
    currentSpace.value = { ...currentSpace.value, name: data.name }
    applyTeamNameUpdate(data.id, data.name)
  } catch {
    /* global toast */
  }
}

function applyTeamNameUpdate(teamId: number, name: string) {
  const idx = spaces.value.findIndex((s) => s.id === teamId)
  if (idx >= 0) spaces.value[idx] = { ...spaces.value[idx], name }
  if (memberContextSpace.value?.id === teamId) {
    memberContextSpace.value = { ...memberContextSpace.value, name }
  }
  if (currentSpace.value?.id === teamId && breadcrumb.value.length > 0 && breadcrumb.value[0].id === currentSpace.value.rootFolderId) {
    breadcrumb.value[0].name = name
  }
}

async function syncMemberContextMeta() {
  const ctx = memberContextSpace.value
  if (!ctx) return
  try {
    const { data } = await http.get(`/api/teams/${ctx.id}`)
    applyTeamNameUpdate(data.id, data.name)
  } catch {
    /* global toast */
  }
}

async function enterSpace(space: TeamSpace) {
  currentSpace.value = space
  currentFolderId.value = space.rootFolderId
  breadcrumb.value = [{ id: space.rootFolderId, name: space.name }]
  await loadFiles()
}

async function loadFiles() {
  if (!currentSpace.value) return
  await syncSpaceMeta()
  loading.value = true
  try {
    const { data } = await http.get(`/api/teams/${currentSpace.value.id}/files`, {
      params: {
        folderId:
          currentFolderId.value !== currentSpace.value.rootFolderId ? currentFolderId.value : undefined
      }
    })
    files.value = data.items || []
  } catch {
    files.value = []
  } finally {
    loading.value = false
  }
}

async function openMembers() {
  memberContextSpace.value = null
  showMembers.value = true
  await syncSpaceMeta()
  await loadMembers()
}

async function openMembersForTeam(team: TeamSpace) {
  memberContextSpace.value = { ...team }
  showMembers.value = true
  await syncMemberContextMeta()
  await loadMembers()
}

async function loadMembers() {
  const ctx = membersContext.value
  if (!ctx) return
  membersLoading.value = true
  avatarBroken.value = {}
  try {
    const { data } = await http.get(`/api/teams/${ctx.id}/members`)
    members.value = data
    const me = members.value.find((m) => m.username === auth.username)
    if (me) {
      if (memberContextSpace.value?.id === ctx.id) {
        memberContextSpace.value = { ...memberContextSpace.value, myRole: me.role }
      } else if (currentSpace.value?.id === ctx.id) {
        currentSpace.value = { ...currentSpace.value, myRole: me.role }
      }
    }
  } catch {
    /* global toast */
  } finally {
    membersLoading.value = false
  }
}

function enterFolder(row: FileItem) {
  if (row.type !== 'folder') return
  breadcrumb.value.push({ id: row.id, name: row.name })
  currentFolderId.value = row.id
  loadFiles()
}

function gotoCrumb(idx: number) {
  const target = breadcrumb.value[idx]
  breadcrumb.value = breadcrumb.value.slice(0, idx + 1)
  currentFolderId.value = target.id
  loadFiles()
}

function goBackFolder() {
  if (breadcrumb.value.length <= 1) return
  const target = breadcrumb.value[breadcrumb.value.length - 2]
  breadcrumb.value = breadcrumb.value.slice(0, -1)
  currentFolderId.value = target.id
  loadFiles()
}

function backToList() {
  currentSpace.value = null
  files.value = []
  members.value = []
  breadcrumb.value = []
}

function downloadFile(row: FileItem) {
  transferStore.addDownloadTask(row.id, row.name, row.sizeBytes || 0)
}

async function resolvePreviewUrl(fileId: number): Promise<string> {
  try {
    const { data } = await http.get(`/api/files/${fileId}/direct-url`)
    if (data.url) return data.url
  } catch {
    /* fallback proxy preview */
  }
  return `/api/files/${fileId}/preview?access_token=${tokenParam()}`
}

async function previewFile(row: FileItem) {
  if (!row.previewable) {
    ElMessage.info('该文件类型暂不支持预览')
    return
  }
  previewName.value = row.name
  previewType.value = row.mimeType || ''
  previewUrl.value = await resolvePreviewUrl(row.id)
  previewVisible.value = true
}

async function refreshAfterUpload() {
  await loadFiles()
}

async function processFiles(fileList: File[]) {
  await transferStore.processFiles(fileList, currentFolderId.value, refreshAfterUpload)
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.length) void processFiles(Array.from(input.files))
  input.value = ''
}

function onFolderChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.length) void processFiles(Array.from(input.files))
  input.value = ''
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  dragOver.value = false
  if (e.dataTransfer?.files?.length) void processFiles(Array.from(e.dataTransfer.files))
}

function onDragEnter() {
  dragOver.value = true
}

function onDragLeave(e: DragEvent) {
  const related = e.relatedTarget as Node | null
  if (!related || !(e.currentTarget as HTMLElement).contains(related)) {
    dragOver.value = false
  }
}

async function createFolder() {
  const ok = await promptCreateFolder(currentFolderId.value)
  if (ok) await loadFiles()
}

function onWsProgress(data: {
  type?: string
  taskId?: string
  progress?: number
  status?: string
}) {
  if (!data.taskId) return
  transferStore.updateProgress(data.taskId, data.progress ?? 0, data.status)
}

function openInviteDialog() {
  inviteUsername.value = ''
  inviteVisible.value = true
}

async function submitInvite() {
  const ctx = membersContext.value
  if (!ctx) return
  const username = inviteUsername.value.trim()
  if (!username) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (inviting.value) return
  inviting.value = true
  try {
    await http.post(`/api/teams/${ctx.id}/members`, {
      username,
      role: 'MEMBER'
    })
    inviteVisible.value = false
    ElMessage.success('邀请已发送，等待对方确认')
    await loadMembers()
  } catch {
    /* global toast */
  } finally {
    inviting.value = false
  }
}

async function removeMember(member: TeamMember) {
  const ctx = membersContext.value
  if (!ctx) return
  await ElMessageBox.confirm(`确定将「${memberDisplayName(member)}」移出团队吗？`, '移除成员', {
    type: 'warning'
  })
  try {
    await http.delete(`/api/teams/${ctx.id}/members/${member.userId}`)
    ElMessage.success('已移除')
    await loadMembers()
  } catch {
    /* global toast */
  }
}

async function disbandSpace(space?: TeamSpace) {
  const target = space || currentSpace.value
  if (!target) return
  await ElMessageBox.confirm(
    `确定解散团队「${target.name}」吗？所有成员将被移除，团队关联文件将被移入回收站！`,
    '解散团队',
    { type: 'warning' }
  )
  try {
    await http.delete(`/api/teams/${target.id}`)
    ElMessage.success('已解散该团队空间')
    if (currentSpace.value?.id === target.id) backToList()
    await loadSpaces()
  } catch {
    /* global toast */
  }
}

async function leaveSpace(space?: TeamSpace) {
  const target = space || currentSpace.value
  if (!target) return
  await ElMessageBox.confirm(
    `确定退出团队「${target.name}」吗？退出后您将无法再访问其共享文件！`,
    '退出团队',
    { type: 'warning' }
  )
  try {
    await http.post(`/api/teams/${target.id}/leave`)
    ElMessage.success('已成功退出该团队空间')
    if (currentSpace.value?.id === target.id) backToList()
    await loadSpaces()
  } catch {
    /* global toast */
  }
}

async function deleteItem(row: FileItem) {
  await ElMessageBox.confirm(`确定删除「${row.name}」吗？文件/目录将被移至回收站`, '删除', {
    type: 'warning'
  })
  const url = row.type === 'folder' ? `/api/folders/${row.id}` : `/api/files/${row.id}`
  try {
    await http.delete(url)
    ElMessage.success('已移至回收站')
    await loadFiles()
  } catch {
    /* global toast */
  }
}

function onTeamCommand(command: string, team: TeamSpace) {
  if (command === 'members') void openMembersForTeam(team)
  else if (command === 'rename') openRenameDialog(team)
  else if (command === 'disband') void disbandSpace(team)
  else if (command === 'leave') void leaveSpace(team)
}

onMounted(() => {
  connectUploadWs(onWsProgress)
  void loadSpaces()
})

onUnmounted(disconnectUploadWs)
</script>

<template>
  <div class="cd-page">
    <!-- 团队列表 -->
    <el-card v-if="!currentSpace" shadow="never" class="cd-page-card" v-loading="loading">
      <PageHeader
        title="团队空间"
        description="创建或加入团队，与成员共享文件与目录"
        :icon="TeamSpaceIcon"
        :count="spaces.length"
        count-label="个团队"
      >
        <template #actions>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            创建团队
          </el-button>
        </template>
      </PageHeader>

      <div class="cd-team-body">
        <el-empty v-if="!spaces.length && !loading" description="还没有团队" class="cd-page-empty">
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            创建团队
          </el-button>
        </el-empty>

        <div v-else class="cd-team-list">
          <div
            v-for="space in spaces"
            :key="space.id"
            class="cd-team-row"
            @click="enterSpace(space)"
          >
            <div class="cd-team-avatar" :style="teamAvatarStyle(space.id)">
              {{ space.name.charAt(0).toUpperCase() }}
            </div>
            <div class="cd-team-info">
              <div class="cd-team-name">{{ space.name }}</div>
              <div class="cd-team-meta">
                <span
                  class="cd-role-badge"
                  :style="{
                    color: roleColor(space.myRole),
                    background: roleColor(space.myRole) + '14',
                    borderColor: roleColor(space.myRole) + '33'
                  }"
                >
                  {{ roleLabel(space.myRole) }}
                </span>
                <span class="cd-team-members">{{ space.memberCount }} 位成员</span>
              </div>
            </div>
            <el-dropdown trigger="click" @command="(cmd: string) => onTeamCommand(cmd, space)">
              <button type="button" class="cd-team-more" @click.stop>
                <el-icon :size="18"><MoreFilled /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="members">成员管理</el-dropdown-item>
                  <el-dropdown-item
                    v-if="space.myRole === 'OWNER' || space.myRole === 'ADMIN'"
                    command="rename"
                  >
                    重命名团队
                  </el-dropdown-item>
                  <el-dropdown-item v-if="space.myRole === 'OWNER'" command="disband">
                    <span class="cd-danger-text">解散团队</span>
                  </el-dropdown-item>
                  <el-dropdown-item v-else command="leave">
                    <span class="cd-danger-text">退出团队</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 团队文件 -->
    <el-card v-else shadow="never" class="cd-page-card cd-team-detail">
      <div class="cd-team-detail-head">
        <div class="cd-team-detail-left">
          <div class="cd-team-avatar cd-team-avatar--detail" :style="teamAvatarStyle(currentSpace.id)">
            {{ currentSpace.name.charAt(0).toUpperCase() }}
          </div>
          <div class="cd-team-detail-title">
            <div class="cd-team-detail-nav">
              <a href="#" class="cd-team-list-link" @click.prevent="backToList">团队空间</a>
              <span class="cd-team-detail-sep">/</span>
              <h2>{{ currentSpace.name }}</h2>
            </div>
            <span class="cd-team-detail-count">共 {{ files.length }} 项</span>
          </div>
        </div>
        <div class="cd-team-detail-actions">
          <el-button @click="openMembers">
            <el-icon><User /></el-icon>
            成员管理
          </el-button>
          <el-button v-if="canManageMembers" @click="openRenameDialog()">
            <el-icon><EditPen /></el-icon>
            重命名
          </el-button>
          <el-button v-if="currentSpace.myRole === 'OWNER'" type="danger" plain @click="disbandSpace()">
            <el-icon><Delete /></el-icon>
            解散团队
          </el-button>
          <el-button v-else type="danger" plain @click="leaveSpace()">
            <el-icon><SwitchButton /></el-icon>
            退出团队
          </el-button>
        </div>
      </div>

      <div v-if="breadcrumb.length > 1" class="cd-team-breadcrumb">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item v-for="(c, i) in breadcrumb" :key="c.id">
            <a href="#" :class="{ active: i === breadcrumb.length - 1 }" @click.prevent="gotoCrumb(i)">
              {{ c.name }}
            </a>
          </el-breadcrumb-item>
        </el-breadcrumb>
        <el-button v-if="breadcrumb.length > 1" link type="primary" size="small" @click="goBackFolder">
          <el-icon><Back /></el-icon>
          返回上级
        </el-button>
      </div>

      <div class="cd-team-toolbar">
        <el-button type="primary" @click="fileInput?.click()">
          <el-icon><Upload /></el-icon>
          上传文件
        </el-button>
        <el-button @click="folderInput?.click()">
          <el-icon><FolderAdd /></el-icon>
          上传文件夹
        </el-button>
        <el-button @click="createFolder">
          <el-icon><FolderAdd /></el-icon>
          新建文件夹
        </el-button>
      </div>

      <div
        class="cd-team-files-wrap"
        :class="{ 'is-dragover': dragOver }"
        @dragover.prevent
        @dragenter.prevent="onDragEnter"
        @dragleave="onDragLeave"
        @drop="onDrop"
      >
        <div v-if="dragOver" class="cd-drop-overlay">
          <div class="cd-drop-overlay-inner">
            <el-icon :size="40"><UploadFilled /></el-icon>
            <p>释放文件以上传到当前目录</p>
          </div>
        </div>

        <div class="cd-team-files">
          <FileGridView
            :items="gridFiles"
            :loading="loading"
            simple
            @open="enterFolder"
            @download="downloadFile"
            @preview="previewFile"
            @delete="deleteItem"
          >
            <template #empty>
              <div class="cd-team-empty">
                <div class="cd-team-empty-icon">
                  <el-icon :size="36"><FolderOpened /></el-icon>
                </div>
                <h3>团队空间为空</h3>
                <p>还没有文件，上传后即可与成员共享查看</p>
                <el-button type="primary" class="cd-team-empty-btn" @click="fileInput?.click()">
                  <el-icon><Upload /></el-icon>
                  上传文件
                </el-button>
              </div>
            </template>
          </FileGridView>
        </div>
      </div>

      <input ref="fileInput" type="file" multiple hidden @change="onFileChange" />
      <input ref="folderInput" type="file" webkitdirectory multiple hidden @change="onFolderChange" />
    </el-card>

    <!-- 创建团队 -->
    <el-dialog v-model="createVisible" title="创建团队空间" width="420px" destroy-on-close>
      <p class="cd-dialog-desc">与成员共享和管理文件</p>
      <el-input v-model="createName" placeholder="输入团队名称" maxlength="64" @keyup.enter="submitCreate" />
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 重命名团队 -->
    <el-dialog v-model="renameVisible" title="重命名团队" width="420px" destroy-on-close>
      <el-input v-model="renameName" placeholder="输入新的团队名称" maxlength="64" @keyup.enter="submitRename" />
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" :loading="renaming" @click="submitRename">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文件预览 -->
    <el-dialog
      v-model="previewVisible"
      :title="previewName"
      width="90%"
      destroy-on-close
      top="4vh"
      class="cd-preview-dialog"
    >
      <img v-if="isImage" :src="previewUrl" class="cd-preview-media" alt="preview" />
      <VideoPreview v-else-if="isVideo" :src="previewUrl" />
      <PdfPreview v-else-if="isPdf" :src="previewUrl" />
      <el-empty v-else description="暂不支持该类型预览" />
    </el-dialog>

    <!-- 邀请成员 -->
    <el-dialog v-model="inviteVisible" title="邀请成员" width="420px" destroy-on-close>
      <p class="cd-dialog-desc">输入对方登录用户名即可邀请加入团队，对方确认后才会加入</p>
      <el-input
        v-model="inviteUsername"
        placeholder="请输入被邀请人的用户名"
        @keyup.enter="submitInvite"
      />
      <template #footer>
        <el-button @click="inviteVisible = false">取消</el-button>
        <el-button type="primary" :loading="inviting" @click="submitInvite">邀请</el-button>
      </template>
    </el-dialog>

    <!-- 成员管理 -->
    <el-drawer
      v-model="showMembers"
      size="440px"
      :show-header="false"
      class="cd-member-drawer"
      destroy-on-close
      @closed="memberContextSpace = null"
    >
      <div class="cd-member-shell">
        <div v-if="membersContext" class="cd-member-hero-card">
          <button type="button" class="cd-member-close" aria-label="关闭" @click="showMembers = false">
            <el-icon :size="16"><Close /></el-icon>
          </button>
          <div class="cd-member-hero-top">
            <div class="cd-member-team-avatar" :style="teamAvatarStyle(membersContext.id)">
              {{ membersContext.name.charAt(0).toUpperCase() }}
            </div>
            <div class="cd-member-team-text">
              <div class="cd-member-team-name">{{ membersContext.name }}</div>
              <div class="cd-member-title">成员管理</div>
            </div>
          </div>
          <div class="cd-member-hero-foot">
            <span class="cd-member-count-chip">{{ members.length }} 位成员</span>
            <button
              v-if="canManageMembers"
              type="button"
              class="cd-member-invite-btn"
              @click="openInviteDialog"
            >
              <el-icon :size="14"><Plus /></el-icon>
              邀请成员
            </button>
          </div>
        </div>

        <div v-loading="membersLoading" class="cd-member-panel">
          <el-empty v-if="!membersLoading && !members.length" description="暂无成员" :image-size="72" />
          <div v-for="member in members" :key="member.userId" class="cd-member-card">
            <div class="cd-member-main">
              <el-avatar
                :size="48"
                :src="memberAvatarSrc(member) || undefined"
                class="cd-member-avatar"
                @error="onMemberAvatarError(member.userId)"
              >
                {{ memberInitial(member) }}
              </el-avatar>
              <div class="cd-member-text">
                <div class="cd-member-name">{{ memberDisplayName(member) }}</div>
                <div class="cd-member-time">
                  加入时间 {{ new Date(member.joinTime).toLocaleDateString() }}
                </div>
              </div>
            </div>
            <div class="cd-member-actions">
              <span
                class="cd-member-role"
                :style="{
                  color: roleColor(member.role),
                  background: roleColor(member.role) + '14',
                  borderColor: roleColor(member.role) + '33'
                }"
              >
                {{ roleLabel(member.role) }}
              </span>
              <button
                v-if="
                  member.role !== 'OWNER' &&
                  canManageMembers &&
                  member.username !== auth.username
                "
                type="button"
                class="cd-member-remove-btn"
                @click="removeMember(member)"
              >
                移除
              </button>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.cd-team-body {
  min-height: 0;
}

.cd-team-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 20px 20px;
}

.cd-team-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid var(--cd-border-light);
  border-radius: var(--cd-radius-lg);
  background: #fff;
  cursor: pointer;
  transition: var(--cd-transition);
}

.cd-team-row:hover {
  border-color: color-mix(in srgb, var(--cd-primary) 25%, var(--cd-border-light));
  box-shadow: var(--cd-shadow-sm);
  transform: translateY(-1px);
}

.cd-team-avatar {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  flex-shrink: 0;
}

.cd-team-info {
  flex: 1;
  min-width: 0;
}

.cd-team-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--cd-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cd-team-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}

.cd-role-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 600;
}

.cd-team-members {
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.cd-team-more {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--cd-text-placeholder);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: var(--cd-transition-fast);
}

.cd-team-more:hover {
  background: var(--cd-bg);
  color: var(--cd-text-secondary);
}

.cd-danger-text {
  color: var(--cd-danger);
}

.cd-team-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--cd-border-light);
}

.cd-team-detail-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.cd-team-avatar--detail {
  width: 52px;
  height: 52px;
  font-size: 22px;
}

.cd-team-detail-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.cd-team-list-link {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--cd-text-secondary);
  text-decoration: none;
  transition: var(--cd-transition-fast);
}

.cd-team-list-link:hover {
  color: var(--cd-primary);
}

.cd-team-detail-sep {
  flex-shrink: 0;
  color: var(--cd-text-placeholder);
  font-size: 13px;
}

.cd-team-detail-nav h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--cd-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cd-team-detail-title h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--cd-text-primary);
}

.cd-team-detail-count {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.cd-team-detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.cd-team-breadcrumb {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 22px;
  border-bottom: 1px solid var(--cd-border-light);
  background: color-mix(in srgb, var(--theme-bg) 30%, #fff);
}

.cd-team-breadcrumb a {
  color: var(--cd-text-secondary);
  text-decoration: none;
}

.cd-team-breadcrumb a:hover,
.cd-team-breadcrumb a.active {
  color: var(--cd-primary);
}

.cd-team-files-wrap {
  position: relative;
  min-height: 360px;
}

.cd-team-files-wrap.is-dragover .cd-team-files {
  filter: blur(1px);
  opacity: 0.72;
  pointer-events: none;
}

.cd-drop-overlay {
  position: absolute;
  inset: 18px 20px 24px;
  z-index: 20;
  border-radius: var(--cd-radius-lg);
  border: 2px dashed var(--cd-primary);
  background: color-mix(in srgb, var(--cd-primary) 8%, rgba(255, 255, 255, 0.92));
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.cd-drop-overlay-inner {
  text-align: center;
  color: var(--cd-primary);
}

.cd-drop-overlay-inner p {
  margin: 12px 0 0;
  font-size: 15px;
  font-weight: 600;
}

.cd-team-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 14px 22px;
  border-bottom: 1px solid var(--cd-border-light);
  background: #fff;
}

.cd-team-files {
  padding: 18px 20px 24px;
}

.cd-team-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  text-align: center;
  padding: 40px 20px;
}

.cd-team-empty-icon {
  width: 76px;
  height: 76px;
  border-radius: 22px;
  background: var(--theme-primary-muted);
  color: var(--cd-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.cd-team-empty h3 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
}

.cd-team-empty p {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--cd-text-secondary);
}

.cd-team-empty-btn {
  margin-top: 4px;
}

.cd-dialog-desc {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--cd-text-secondary);
  line-height: 1.5;
}

.cd-member-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: color-mix(in srgb, var(--theme-bg) 55%, #fff);
}

.cd-member-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
  padding: 18px 18px 24px;
}

.cd-member-hero-card {
  position: relative;
  padding: 18px 18px 16px;
  border-radius: 18px;
  background: linear-gradient(145deg, #fff 0%, color-mix(in srgb, var(--theme-primary) 4%, #fff) 100%);
  border: 1px solid color-mix(in srgb, var(--theme-primary) 14%, var(--cd-border-light));
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.cd-member-close {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 32px;
  height: 32px;
  border: 1px solid var(--cd-border-light);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  color: var(--cd-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: var(--cd-transition-fast);
}

.cd-member-close:hover {
  color: var(--cd-text-primary);
  border-color: var(--cd-border);
  background: #fff;
}

.cd-member-hero-top {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-right: 36px;
}

.cd-member-team-avatar {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 24px;
  font-weight: 800;
  flex-shrink: 0;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.14);
}

.cd-member-team-text {
  min-width: 0;
}

.cd-member-team-name {
  font-size: 22px;
  font-weight: 800;
  color: var(--cd-text-primary);
  letter-spacing: -0.02em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cd-member-title {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--cd-text-secondary);
}

.cd-member-hero-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid color-mix(in srgb, var(--cd-border-light) 80%, transparent);
}

.cd-member-count-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 12px;
  border-radius: 999px;
  background: #f1f5f9;
  font-size: 12px;
  font-weight: 700;
  color: var(--cd-text-secondary);
}

.cd-member-invite-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--cd-border);
  border-radius: 999px;
  background: #fff;
  color: var(--cd-text-primary);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: var(--cd-transition-fast);
}

.cd-member-invite-btn:hover {
  border-color: color-mix(in srgb, var(--cd-primary) 35%, var(--cd-border));
  background: color-mix(in srgb, var(--theme-primary) 6%, #fff);
  color: var(--cd-primary);
}

.cd-member-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 120px;
}

.cd-member-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid var(--cd-border-light);
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.04);
  transition: var(--cd-transition-fast);
}

.cd-member-card:hover {
  border-color: color-mix(in srgb, var(--cd-primary) 18%, var(--cd-border-light));
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}

.cd-member-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.cd-member-avatar {
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%) !important;
  color: #fff !important;
  font-weight: 700 !important;
  flex-shrink: 0;
  border: 2px solid #fff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.1);
}

.cd-member-text {
  min-width: 0;
}

.cd-member-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--cd-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cd-member-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.cd-member-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.cd-member-role {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.cd-member-remove-btn {
  padding: 5px 12px;
  border: 1px solid rgba(239, 68, 68, 0.18);
  border-radius: 8px;
  background: rgba(254, 242, 242, 0.9);
  color: var(--cd-danger);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: var(--cd-transition-fast);
}

.cd-member-remove-btn:hover {
  background: rgba(254, 226, 226, 1);
  border-color: rgba(239, 68, 68, 0.28);
}

.cd-preview-dialog :deep(.el-dialog__header) {
  padding-bottom: 8px !important;
}

.cd-preview-dialog :deep(.el-dialog__body) {
  padding-top: 0 !important;
}

.cd-preview-media {
  max-width: 100%;
  max-height: 75vh;
  display: block;
  margin: 0 auto;
  border-radius: var(--cd-radius);
}
</style>
