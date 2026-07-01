<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import TeamSpaceIcon from '@/components/icons/TeamSpaceIcon.vue'
import http from '@/api/http'
import { resolveFilePreviewUrl } from '@/utils/fileUrl'
import { mediaApiUrl, appendQueryParam } from '@/utils/mediaUrl'
import { useAuthStore } from '@/stores/auth'
import { useConfirmDialogStore } from '@/stores/confirmDialog'
import PageHeader from '@/components/PageHeader.vue'
import FileGridView from '@/components/FileGridView.vue'
import PdfPreview from '@/components/PdfPreview.vue'
import VideoPreview from '@/components/VideoPreview.vue'
import TextPreview from '@/components/TextPreview.vue'
import { isTextFile } from '@/utils/filePreview'
import type { FileItem } from '@/stores/file'
import { useTransferStore, promptCreateFolder } from '@/stores/transfer'
import { connectUploadWs, disconnectUploadWs } from '@/utils/ws'
import { bumpTeamAvatarVersion, getTeamAvatarVersion } from '@/utils/teamAvatar'
import { buildZipDisplayName, buildZipDownloadUrl } from '@/utils/download'
import CachedEntityAvatar from '@/components/CachedEntityAvatar.vue'
import MemberCachedAvatar from '@/components/MemberCachedAvatar.vue'
import {
  cacheEntityAvatarFromFile,
  teamAvatarCacheKey
} from '@/utils/entityAvatarCache'

const auth = useAuthStore()
const transferStore = useTransferStore()
const confirmDialog = useConfirmDialogStore()

interface TeamSpace {
  id: number
  name: string
  ownerId: number
  rootFolderId: number
  myRole: string
  memberCount: number
  createdAt: string
  avatar?: string
  maxSize?: number
  usedBytes?: number
  usedPercent?: number
  usedFormatted?: string
  maxSizeFormatted?: string
}

interface TeamAccessInfo {
  role: string
  canWrite: boolean
  canManageTeam: boolean
  canShare: boolean
  canDeleteAny: boolean
  usedBytes?: number
  maxSize?: number
  usedPercent?: number
  usedFormatted?: string
  maxSizeFormatted?: string
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

const selectedItems = ref<FileItem[]>([])
const selectedIds = computed({
  get: () => selectedItems.value.map(item => item.id),
  set: (ids) => {
    selectedItems.value = files.value.filter(item => ids.includes(item.id))
  }
})

function clearSelection() {
  selectedItems.value = []
}

watch(currentFolderId, () => {
  clearSelection()
})

function handleBatchDownload() {
  const folders = selectedItems.value.filter(i => i.type === 'folder').map(i => i.id)
  const files = selectedItems.value.filter(i => i.type === 'file').map(i => i.id)
  if (folders.length === 0 && files.length === 0) return
  transferStore.addZipDownloadTask(
    buildZipDownloadUrl(folders, files),
    buildZipDisplayName(selectedItems.value)
  )
}

async function handleBatchDelete() {
  const deletable = selectedItems.value.filter((i) => i.canDelete !== false)
  if (deletable.length === 0) {
    ElMessage.warning('所选项目无权删除')
    return
  }
  const ok = await confirmDialog.open({
    title: '批量删除',
    message: `确定要批量删除选中的 ${deletable.length} 个项目吗？此操作将移入回收站！`,
    confirmText: '删除',
    danger: true
  })
  if (!ok) return
  try {
    for (const item of deletable) {
      const url = item.type === 'folder' ? `/api/folders/${item.id}` : `/api/files/${item.id}`
      await http.delete(url)
    }
    ElMessage.success('批量删除成功')
    clearSelection()
    await loadFiles()
  } catch {
    /* error */
  }
}
const membersLoading = ref(false)
const avatarBroken = ref<Record<number, boolean>>({})
/** 从团队列表直接进入成员管理时使用，避免误切到文件详情页 */
const memberContextSpace = ref<TeamSpace | null>(null)

const membersContext = computed(() => memberContextSpace.value ?? currentSpace.value)

const teamAccess = ref<TeamAccessInfo | null>(null)

const canWriteTeam = computed(() => {
  if (teamAccess.value) return teamAccess.value.canWrite
  const role = currentSpace.value?.myRole
  return !!role && role !== 'VIEWER'
})

const canManageMembers = computed(
  () => membersContext.value && (membersContext.value.myRole === 'OWNER' || membersContext.value.myRole === 'ADMIN')
)
const createName = ref('')
const creating = ref(false)
const inviteVisible = ref(false)
const inviteUsername = ref('')
const inviteRole = ref('MEMBER')
const inviting = ref(false)
const renameVisible = ref(false)
const renameName = ref('')
const renameTarget = ref<TeamSpace | null>(null)
const renaming = ref(false)

const teamAvatarUploading = ref(false)
const teamAvatarInputRef = ref<HTMLInputElement | null>(null)

function teamAvatarSrc(space: TeamSpace) {
  if (!space.avatar) return ''
  const v = getTeamAvatarVersion(space.id)
  return appendQueryParam(mediaApiUrl(`/api/teams/${space.id}/avatar`), 'v', v)
}

function openTeamAvatarPicker() {
  if (!teamAvatarUploading.value) teamAvatarInputRef.value?.click()
}

async function onTeamAvatarSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !currentSpace.value) return

  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }

  teamAvatarUploading.value = true
  const formData = new FormData()
  formData.append('file', file)

  try {
    const { data } = await http.post(`/api/teams/${currentSpace.value.id}/avatar`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('团队头像已更新')
    bumpTeamAvatarVersion(currentSpace.value.id)
    const v = getTeamAvatarVersion(currentSpace.value.id)
    void cacheEntityAvatarFromFile(teamAvatarCacheKey(currentSpace.value.id), v, file).catch(() => {})
    currentSpace.value = { ...currentSpace.value, avatar: data.avatar }
    
    const idx = spaces.value.findIndex(s => s.id === currentSpace.value?.id)
    if (idx >= 0) {
      spaces.value[idx] = { ...spaces.value[idx], avatar: data.avatar }
    }
  } catch {
    /* global toast */
  } finally {
    teamAvatarUploading.value = false
  }
}

const fileInput = ref<HTMLInputElement | null>(null)
const folderInput = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)
const previewVisible = ref(false)
const videoPreviewRef = ref<InstanceType<typeof VideoPreview> | null>(null)
const previewUrl = ref('')
const previewType = ref('')
const previewName = ref('')

const isDialogFullscreen = ref(false)

function toggleDialogFullscreen() {
  const el = document.querySelector('.cd-preview-dialog')
  if (!el) return
  if (!document.fullscreenElement) {
    el.requestFullscreen().catch((err) => {
      console.error('全屏失败:', err)
    })
  } else {
    document.exitFullscreen()
  }
}

function handleDialogFullscreenChange() {
  const el = document.querySelector('.cd-preview-dialog')
  isDialogFullscreen.value = document.fullscreenElement === el
}

function onPreviewClosed() {
  videoPreviewRef.value?.stop?.()
  if (document.fullscreenElement) {
    document.exitFullscreen()
  }
}

watch(previewVisible, (visible) => {
  if (!visible) {
    onPreviewClosed()
  }
})

const teamGradients = [
  'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)',
  'linear-gradient(135deg, #0ea5e9 0%, #2563eb 100%)',
  'linear-gradient(135deg, #10b981 0%, #059669 100%)',
  'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
  'linear-gradient(135deg, #ec4899 0%, #db2777 100%)',
  'linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%)'
]

const gridFiles = computed(() => files.value)

const createVisible = ref(false)

const isImage = computed(() => previewType.value.startsWith('image/'))
const isVideo = computed(() => previewType.value.startsWith('video/'))
const isPdf = computed(() => previewType.value.includes('pdf'))
const isText = computed(() => isTextFile(previewType.value, previewName.value))

function teamAvatarStyle(teamId: number) {
  return { background: teamGradients[teamId % teamGradients.length] }
}

function roleLabel(role: string) {
  switch (role) {
    case 'OWNER':
      return '创建者'
    case 'ADMIN':
      return '管理员'
    case 'VIEWER':
      return '只读成员'
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
    case 'VIEWER':
      return '#6366f1'
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
  if (member.username === auth.username && auth.avatarDisplaySrc) return auth.avatarDisplaySrc
  const hasAvatar = member.hasAvatar ?? !!member.avatar
  if (!hasAvatar) return ''
  const ctx = membersContext.value
  if (!ctx) return ''
  return mediaApiUrl(`/api/teams/${ctx.id}/members/${member.userId}/avatar`)
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
  clearSelection()
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
    teamAccess.value = data.teamAccess || null
    if (teamAccess.value && currentSpace.value) {
      currentSpace.value = { ...currentSpace.value, myRole: teamAccess.value.role }
    }
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

/** 网格卡片单击：进入文件夹 / 预览文件 / 非可预览则下载 */
function handleGridOpen(row: FileItem) {
  if (row.type === 'folder') {
    enterFolder(row)
  } else if (row.previewable) {
    previewFile(row)
  } else {
    downloadFile(row)
  }
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
  if (row.type === 'folder') {
    transferStore.addZipDownloadTask(
      buildZipDownloadUrl([row.id], []),
      `${row.name}.zip`
    )
    return
  }
  transferStore.addDownloadTask(row.id, row.name, row.sizeBytes || 0)
}

async function resolvePreviewUrl(fileId: number): Promise<string> {
  return resolveFilePreviewUrl(fileId)
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
  if (!canWriteTeam.value) return
  e.preventDefault()
  dragOver.value = false
  if (e.dataTransfer?.files?.length) void processFiles(Array.from(e.dataTransfer.files))
}

function onDragEnter() {
  if (!canWriteTeam.value) return
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
  inviteRole.value = 'MEMBER'
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

  try {
    await ElMessageBox.confirm(
      `确认向用户 "${username}" 发送团队加入邀请吗？`,
      '发送团队邀请',
      {
        confirmButtonText: '确认发送',
        cancelButtonText: '取消',
        type: 'info',
        roundButton: true
      }
    )
  } catch {
    return
  }

  if (inviting.value) return
  inviting.value = true
  try {
    await http.post(`/api/teams/${ctx.id}/members`, {
      username,
      role: inviteRole.value
    })
    inviteVisible.value = false
    ElNotification({
      title: '邀请已发送',
      message: `已成功向 "${username}" 发出邀请，等待对方在其消息中心确认加入。`,
      type: 'success',
      duration: 4500,
      position: 'top-right'
    })
    await loadMembers()
  } catch {
    /* global toast */
  } finally {
    inviting.value = false
  }
}

async function updateMemberRole(member: TeamMember, role: string) {
  const ctx = membersContext.value
  if (!ctx) return
  try {
    await http.put(`/api/teams/${ctx.id}/members/${member.userId}/role`, { role })
    ElMessage.success('成员角色已更新')
    await loadMembers()
    if (currentSpace.value?.id === ctx.id) {
      await loadFiles()
    }
  } catch {
    /* global toast */
  }
}

const quotaVisible = ref(false)
const quotaGb = ref(0)
const quotaUnlimited = ref(true)
const quotaSaving = ref(false)

function openQuotaDialog() {
  const max = currentSpace.value?.maxSize ?? 0
  quotaUnlimited.value = !max || max <= 0
  quotaGb.value = max > 0 ? Math.round((max / (1024 ** 3)) * 10) / 10 : 0
  quotaVisible.value = true
}

async function submitQuota() {
  if (!currentSpace.value) return
  const gb = Number(quotaGb.value)
  if (!quotaUnlimited.value && (isNaN(gb) || gb <= 0)) {
    ElMessage.warning('请输入有效的配额大小')
    return
  }
  const maxSize = quotaUnlimited.value ? 0 : Math.round(gb * 1024 ** 3)
  quotaSaving.value = true
  try {
    await http.put(`/api/teams/${currentSpace.value.id}/quota`, { maxSize })
    ElMessage.success('团队配额已更新')
    quotaVisible.value = false
    await loadSpaces()
    if (currentSpace.value) {
      const updated = spaces.value.find((s) => s.id === currentSpace.value?.id)
      if (updated) currentSpace.value = { ...currentSpace.value, ...updated }
      await loadFiles()
    }
  } catch {
    /* global toast */
  } finally {
    quotaSaving.value = false
  }
}

async function removeMember(member: TeamMember) {
  const ctx = membersContext.value
  if (!ctx) return
  const ok = await confirmDialog.open({
    title: '移除成员',
    message: `确定将「${memberDisplayName(member)}」移出团队吗？`,
    confirmText: '移除',
    danger: true
  })
  if (!ok) return
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
  const ok = await confirmDialog.open({
    title: '解散团队',
    message: `确定解散团队「${target.name}」吗？所有成员将被移除，团队关联文件将被移入回收站！`,
    confirmText: '解散',
    danger: true
  })
  if (!ok) return
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
  const ok = await confirmDialog.open({
    title: '退出团队',
    message: `确定退出团队「${target.name}」吗？退出后您将无法再访问其共享文件！`,
    confirmText: '退出',
    danger: true
  })
  if (!ok) return
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
  const ok = await confirmDialog.open({
    title: '删除项目',
    message: `确定删除「${row.name}」？此操作将移入回收站！`,
    confirmText: '删除',
    danger: true
  })
  if (!ok) return
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
  document.addEventListener('fullscreenchange', handleDialogFullscreenChange)
})

onUnmounted(() => {
  disconnectUploadWs()
  document.removeEventListener('fullscreenchange', handleDialogFullscreenChange)
})
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
        <div v-if="!spaces.length && !loading" class="cd-team-empty cd-team-empty--page">
          <div class="cd-team-empty-icon-wrapper">
            <div class="cd-team-empty-icon-bg" />
            <div class="cd-team-empty-icon is-clickable" @click="openCreateDialog">
              <el-icon :size="32"><Plus /></el-icon>
            </div>
          </div>
          <h3>还没有团队空间</h3>
          <p>创建或加入团队，与成员共享文件与目录</p>
        </div>

        <div v-else class="cd-team-list">
          <div
            v-for="space in spaces"
            :key="space.id"
            class="cd-team-row"
            @click="enterSpace(space)"
          >
            <div class="cd-team-avatar" :style="space.avatar ? {} : teamAvatarStyle(space.id)">
              <CachedEntityAvatar
                v-if="space.avatar"
                :cache-key="teamAvatarCacheKey(space.id)"
                :src="teamAvatarSrc(space)"
                :version="getTeamAvatarVersion(space.id)"
                img-class="cd-team-avatar-img"
              />
              <span v-else>{{ space.name.charAt(0).toUpperCase() }}</span>
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
          <div v-if="!canManageMembers" class="cd-team-avatar cd-team-avatar--detail" :style="currentSpace.avatar ? {} : teamAvatarStyle(currentSpace.id)">
            <CachedEntityAvatar
              v-if="currentSpace.avatar"
              :cache-key="teamAvatarCacheKey(currentSpace.id)"
              :src="teamAvatarSrc(currentSpace)"
              :version="getTeamAvatarVersion(currentSpace.id)"
              img-class="cd-team-avatar-img"
            />
            <span v-else>{{ currentSpace.name.charAt(0).toUpperCase() }}</span>
          </div>
          <button
            v-else
            type="button"
            class="cd-team-avatar-zone"
            :class="{ uploading: teamAvatarUploading }"
            :disabled="teamAvatarUploading"
            title="点击更换团队头像"
            @click="openTeamAvatarPicker"
          >
            <div class="cd-team-avatar cd-team-avatar--detail" :style="currentSpace.avatar ? {} : teamAvatarStyle(currentSpace.id)">
              <CachedEntityAvatar
              v-if="currentSpace.avatar"
              :cache-key="teamAvatarCacheKey(currentSpace.id)"
              :src="teamAvatarSrc(currentSpace)"
              :version="getTeamAvatarVersion(currentSpace.id)"
              img-class="cd-team-avatar-img"
            />
              <span v-else>{{ currentSpace.name.charAt(0).toUpperCase() }}</span>
            </div>
            <span class="cd-team-avatar-mask">
              <el-icon v-if="!teamAvatarUploading" :size="16"><Camera /></el-icon>
              <el-icon v-else :size="16" class="is-loading"><Loading /></el-icon>
            </span>
          </button>
          <input
            ref="teamAvatarInputRef"
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            style="display: none"
            @change="onTeamAvatarSelected"
          />
          <div class="cd-team-detail-title">
            <div class="cd-team-detail-nav">
              <a href="#" class="cd-team-list-link" @click.prevent="backToList">团队空间</a>
              <span class="cd-team-detail-sep">/</span>
              <h2>{{ currentSpace.name }}</h2>
            </div>
            <div class="cd-team-detail-meta">
              <span class="cd-team-detail-count">共 {{ files.length }} 项</span>
              <span v-if="teamAccess?.maxSize" class="cd-team-detail-count">
                · 已用 {{ teamAccess.usedFormatted }} / {{ teamAccess.maxSizeFormatted }}
              </span>
              <span v-else-if="teamAccess?.usedBytes" class="cd-team-detail-count">
                · 已用 {{ teamAccess.usedFormatted }}
              </span>
            </div>
          </div>
        </div>
        <div class="cd-team-detail-actions">
          <el-button @click="openMembers">
            <el-icon><User /></el-icon>
            成员管理
          </el-button>
          <el-button v-if="canManageMembers" @click="openTeamAvatarPicker">
            <el-icon><Picture /></el-icon>
            更换头像
          </el-button>
          <el-button v-if="canManageMembers" @click="openQuotaDialog">
            <el-icon><Coin /></el-icon>
            存储配额
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

      <div v-if="canWriteTeam" class="cd-team-toolbar">
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
        <div v-if="dragOver && canWriteTeam" class="cd-drop-overlay">
          <div class="cd-drop-overlay-inner">
            <el-icon :size="40"><UploadFilled /></el-icon>
            <p>释放文件以上传到当前目录</p>
          </div>
        </div>

        <div class="cd-team-files">
          <FileGridView
            v-model:selectedIds="selectedIds"
            :items="gridFiles"
            :loading="loading"
            simple
            @open="handleGridOpen"
            @download="downloadFile"
            @preview="previewFile"
            @delete="deleteItem"
          >
            <template #empty>
              <div class="cd-team-empty">
                <div class="cd-team-empty-icon-wrapper">
                  <div class="cd-team-empty-icon-bg" />
                  <div class="cd-team-empty-icon">
                    <el-icon :size="32"><FolderOpened /></el-icon>
                  </div>
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

      <!-- 悬浮批量操作底栏 -->
      <transition name="cd-fade-slide">
        <div v-if="selectedItems.length > 0" class="cd-batch-bar">
          <div class="cd-batch-info">
            已选择 <span class="cd-batch-count">{{ selectedItems.length }}</span> 项
          </div>
          <div class="cd-batch-actions">
            <el-button type="primary" @click="handleBatchDownload">
              <el-icon><Download /></el-icon>
              打包下载
            </el-button>
            <el-button type="danger" plain @click="handleBatchDelete">
              <el-icon><Delete /></el-icon>
              批量删除
            </el-button>
            <el-button @click="clearSelection">取消</el-button>
          </div>
        </div>
      </transition>
    </el-card>

    <!-- 创建团队 -->
    <el-dialog v-model="createVisible" title="创建团队空间" width="420px" destroy-on-close>
      <p class="cd-dialog-desc">与成员共享和管理文件</p>
      <el-input v-model="createName" placeholder="输入团队名称" maxlength="64" @keyup.enter="submitCreate" />
      <template #footer>
        <div class="cd-dialog-footer-pills">
          <el-button size="large" @click="createVisible = false">取消</el-button>
          <el-button type="primary" size="large" :loading="creating" @click="submitCreate">创建</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 重命名团队 -->
    <el-dialog v-model="renameVisible" title="重命名团队" width="420px" destroy-on-close>
      <el-input v-model="renameName" placeholder="输入新的团队名称" maxlength="64" @keyup.enter="submitRename" />
      <template #footer>
        <div class="cd-dialog-footer-pills">
          <el-button size="large" @click="renameVisible = false">取消</el-button>
          <el-button type="primary" size="large" :loading="renaming" @click="submitRename">保存</el-button>
        </div>
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
      @closed="onPreviewClosed"
    >
      <!-- 弹窗全屏按钮 -->
      <button
        v-if="previewVisible && !isVideo"
        class="cd-dialog-fullscreen-btn"
        :title="isDialogFullscreen ? '退出全屏 (Esc)' : '全屏'"
        @click="toggleDialogFullscreen"
      >
        <svg v-if="!isDialogFullscreen" viewBox="0 0 24 24" class="cd-fullscreen-icon">
          <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3" />
        </svg>
        <svg v-else viewBox="0 0 24 24" class="cd-fullscreen-icon">
          <path d="M4 14h6v6m10-6h-6v6M4 10h6V4m10 6h-6V4" />
        </svg>
      </button>
      <div v-if="isImage" class="cd-preview-image-wrap">
        <img :src="previewUrl" class="cd-preview-media" alt="preview" />
      </div>
      <VideoPreview
        v-else-if="isVideo"
        ref="videoPreviewRef"
        :key="previewUrl"
        :src="previewUrl"
      />
      <PdfPreview v-else-if="isPdf" :src="previewUrl" />
      <TextPreview v-else-if="isText" :src="previewUrl" />
      <el-empty v-else description="暂不支持该类型预览" />
    </el-dialog>

    <!-- 邀请成员 -->
    <el-dialog v-model="inviteVisible" title="邀请成员" width="420px" destroy-on-close>
      <p class="cd-dialog-desc">输入对方用户名即可邀请加入团队，对方确认后才会加入</p>
      <el-input
        v-model="inviteUsername"
        placeholder="请输入被邀请人的用户名"
        @keyup.enter="submitInvite"
      />
      <div style="margin-top: 12px">
        <div class="cd-dialog-desc" style="margin-bottom: 8px">成员角色</div>
        <el-select v-model="inviteRole" class="cd-invite-role-select">
          <el-option label="成员（可上传/管理自己的文件）" value="MEMBER" />
          <el-option label="只读成员（仅浏览下载）" value="VIEWER" />
          <el-option label="管理员（可管理成员与全部文件）" value="ADMIN" />
        </el-select>
      </div>
      <template #footer>
        <div class="cd-dialog-footer-pills">
          <el-button size="large" @click="inviteVisible = false">取消</el-button>
          <el-button type="primary" size="large" :loading="inviting" @click="submitInvite">邀请</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="quotaVisible" title="团队存储配额" width="420px" destroy-on-close>
      <p class="cd-dialog-desc">设置团队空间最大可用容量，0 表示不限制。当前已用：{{ teamAccess?.usedFormatted || '0 B' }}</p>
      <el-checkbox v-model="quotaUnlimited">不限制容量</el-checkbox>
      <el-input
        v-if="!quotaUnlimited"
        v-model="quotaGb"
        type="number"
        :min="0.1"
        :step="0.1"
        style="width: 100%; margin-top: 12px"
        placeholder="请输入配额大小，例如 10"
      >
        <template #suffix>
          <span class="cd-input-suffix-text">GB</span>
        </template>
      </el-input>
      <template #footer>
        <div class="cd-dialog-footer-pills">
          <el-button size="large" @click="quotaVisible = false">取消</el-button>
          <el-button type="primary" size="large" :loading="quotaSaving" @click="submitQuota">保存</el-button>
        </div>
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
          <div class="cd-member-hero-top">
            <div
              class="cd-member-team-avatar"
              :style="membersContext.avatar ? {} : teamAvatarStyle(membersContext.id)"
            >
              <CachedEntityAvatar
                v-if="membersContext.avatar"
                :cache-key="teamAvatarCacheKey(membersContext.id)"
                :src="teamAvatarSrc(membersContext)"
                :version="getTeamAvatarVersion(membersContext.id)"
                img-class="cd-team-avatar-img"
              />
              <template v-else>{{ membersContext.name.charAt(0).toUpperCase() }}</template>
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
              <MemberCachedAvatar
                :team-id="membersContext?.id || 0"
                :member="member"
                :initial="memberInitial(member)"
                @error="onMemberAvatarError"
              />
              <div class="cd-member-text">
                <div class="cd-member-name">{{ memberDisplayName(member) }}</div>
                <div class="cd-member-time">
                  加入时间 {{ new Date(member.joinTime).toLocaleDateString() }}
                </div>
              </div>
            </div>
            <div class="cd-member-actions">
              <el-select
                v-if="canManageMembers && member.role !== 'OWNER' && member.username !== auth.username"
                :model-value="member.role"
                class="cd-member-role-select"
                size="small"
                @change="(val: string) => updateMemberRole(member, val)"
              >
                <el-option label="只读" value="VIEWER" />
                <el-option label="成员" value="MEMBER" />
                <el-option label="管理员" value="ADMIN" />
              </el-select>
              <span
                v-else
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
.cd-team-avatar-img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.cd-team-avatar-zone {
  position: relative;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  border-radius: 14px;
  overflow: hidden;
  display: inline-flex;
}

.cd-team-avatar-zone:hover .cd-team-avatar-mask,
.cd-team-avatar-zone.uploading .cd-team-avatar-mask {
  opacity: 1;
}

.cd-team-avatar-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s ease;
  border-radius: 14px;
}

.cd-team-body {
  min-height: 0;
}

.cd-team-body:has(.cd-team-empty--page) {
  min-height: 360px;
}

.cd-team-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 20px 20px;
  width: 100%;
  box-sizing: border-box;
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

.cd-team-detail-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.cd-team-detail-count {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  color: var(--cd-text-secondary);
  white-space: nowrap;
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

.cd-page :deep(.cd-page-header-icon) {
  background: var(--cd-primary-gradient) !important;
  color: #ffffff !important;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--cd-primary) 25%, transparent), inset 0 1px 0 rgba(255, 255, 255, 0.25) !important;
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

.cd-team-empty--page {
  padding: 80px 24px !important;
  width: 100%;
}

.cd-team-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  text-align: center;
  padding: 40px 20px;
  position: relative;
  background: radial-gradient(circle at center, color-mix(in srgb, var(--cd-primary) 4%, transparent) 0%, transparent 70%);
}

.cd-team-empty-icon-wrapper {
  position: relative;
  width: 100px;
  height: 100px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cd-team-empty-icon-bg {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, var(--cd-primary) 15%, transparent) 0%, transparent 75%);
  animation: teamPulseGlow 3s ease-in-out infinite;
}

@keyframes teamPulseGlow {
  0%, 100% { transform: scale(0.9); opacity: 0.7; }
  50% { transform: scale(1.1); opacity: 1; }
}

.cd-team-empty-icon {
  position: relative;
  z-index: 1;
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--cd-primary) 8%, transparent) 0%, color-mix(in srgb, var(--cd-primary) 12%, transparent) 100%);
  border: 1px solid color-mix(in srgb, var(--cd-primary) 18%, transparent);
  color: var(--cd-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 
    0 10px 24px color-mix(in srgb, var(--cd-primary) 6%, transparent), 
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), background-color 0.3s;
}

.cd-team-empty-icon.is-clickable {
  cursor: pointer;
}

.cd-team-empty-icon:hover {
  transform: translateY(-4px) scale(1.05);
  background: linear-gradient(135deg, color-mix(in srgb, var(--cd-primary) 12%, transparent) 0%, color-mix(in srgb, var(--cd-primary) 18%, transparent) 100%);
  box-shadow: 
    0 12px 30px color-mix(in srgb, var(--cd-primary) 12%, transparent), 
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.cd-team-empty h3 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: 0.5px;
}

.cd-team-empty p {
  margin: 0 0 20px;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  letter-spacing: 0.2px;
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

.cd-member-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 18px 18px 10px;
}

.cd-member-drawer :deep(.el-drawer__headerbtn) {
  top: 18px;
  right: 18px;
  width: 40px !important;
  height: 40px !important;
  padding: 0 !important;
  border: 1px solid var(--cd-border) !important;
  border-radius: 12px !important;
  background: rgba(255, 255, 255, 0.96) !important;
  color: var(--cd-text-secondary) !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  box-sizing: border-box !important;
  box-shadow: var(--cd-shadow-xs);
  transition: var(--cd-transition-fast);
}

.cd-member-drawer :deep(.el-drawer__headerbtn:hover) {
  color: var(--cd-text-primary) !important;
  border-color: color-mix(in srgb, var(--theme-primary) 22%, var(--cd-border)) !important;
  background: #fff !important;
}

.cd-member-drawer :deep(.el-drawer__close-btn) {
  font-size: 18px !important;
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

.cd-member-hero-top {
  display: flex;
  align-items: center;
  gap: 14px;
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
  overflow: hidden;
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

.cd-member-role-select {
  width: 112px;
  flex-shrink: 0;
}

.cd-member-role-select :deep(.el-select__wrapper) {
  border-radius: 999px !important;
  min-height: 28px;
  height: 28px;
  padding: 0 10px 0 12px;
  box-shadow: 0 0 0 1px var(--cd-border) inset !important;
  background: #fff;
}

.cd-member-role-select :deep(.el-select__selected-item) {
  font-size: 12px;
  font-weight: 700;
}

.cd-invite-role-select {
  width: 100%;
}

.cd-invite-role-select :deep(.el-select__wrapper) {
  border-radius: 999px !important;
  min-height: 42px;
  height: 42px;
  padding: 0 14px;
  box-shadow: 0 0 0 1px var(--cd-border) inset !important;
  background: #fff;
}

.cd-member-remove-btn {
  padding: 5px 14px;
  border: 1px solid rgba(239, 68, 68, 0.18);
  border-radius: 999px;
  background: rgba(254, 242, 242, 0.9);
  color: var(--cd-danger);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: var(--cd-transition-fast);
  white-space: nowrap;
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

/* 悬浮批量操作底栏 */
.cd-batch-bar {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(15, 23, 42, 0.08);
  padding: 12px 24px;
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.12);
  display: flex;
  align-items: center;
  gap: 32px;
  z-index: 1000;
  pointer-events: auto;
  transition: all var(--cd-transition-bounce);
}

.cd-batch-info {
  font-size: 14px;
  color: var(--cd-text-secondary);
  font-weight: 500;
}

.cd-batch-count {
  color: var(--cd-primary);
  font-weight: 700;
  font-size: 16px;
  margin: 0 4px;
}

.cd-batch-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 进出场动画 */
.cd-fade-slide-enter-active,
.cd-fade-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.cd-fade-slide-enter-from,
.cd-fade-slide-leave-to {
  opacity: 0;
  transform: translate(-50%, 20px);
}
</style>
