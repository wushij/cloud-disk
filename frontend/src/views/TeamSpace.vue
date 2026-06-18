<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

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
  role: string
  joinTime: string
}

interface FileItem {
  id: number
  name: string
  type: 'file' | 'folder'
  sizeBytes?: number
  mimeType?: string
  parentId?: number
  folderId?: number
  createdAt?: string
}

const spaces = ref<TeamSpace[]>([])
const currentSpace = ref<TeamSpace | null>(null)
const members = ref<TeamMember[]>([])
const files = ref<FileItem[]>([])
const currentFolderId = ref<number>(0)
const loading = ref(false)
const showMembers = ref(false)

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

async function createSpace() {
  const { value } = await ElMessageBox.prompt('请输入团队空间名称', '创建团队空间', {
    confirmButtonText: '创建',
    cancelButtonText: '取消'
  }).catch(() => ({ value: null }))
  if (!value?.trim()) return
  try {
    await http.post('/api/teams', { name: value.trim() })
    ElMessage.success('创建成功')
    loadSpaces()
  } catch {
    /* global toast */
  }
}

async function enterSpace(space: TeamSpace) {
  currentSpace.value = space
  currentFolderId.value = space.rootFolderId
  await loadFiles()
}

async function loadFiles() {
  if (!currentSpace.value) return
  loading.value = true
  try {
    const { data } = await http.get(`/api/teams/${currentSpace.value.id}/files`, {
      params: { folderId: currentFolderId.value !== currentSpace.value.rootFolderId ? currentFolderId.value : undefined }
    })
    files.value = data.items || []
  } catch {
    /* global toast */
  } finally {
    loading.value = false
  }
}

async function loadMembers() {
  if (!currentSpace.value) return
  const { data } = await http.get(`/api/teams/${currentSpace.value.id}/members`)
  members.value = data
  showMembers.value = true
}

function enterFolder(row: FileItem) {
  if (row.type !== 'folder') return
  currentFolderId.value = row.id
  loadFiles()
}

function goBack() {
  if (!currentSpace.value) return
  currentFolderId.value = currentSpace.value.rootFolderId
  loadFiles()
}

function backToList() {
  currentSpace.value = null
  files.value = []
  members.value = []
}

function fmtSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function downloadFile(row: FileItem) {
  const a = document.createElement('a')
  a.href = `/api/files/${row.id}/download`
  a.download = row.name
  a.click()
}

async function inviteMember() {
  if (!currentSpace.value) return
  const { value } = await ElMessageBox.prompt('请输入用户名', '邀请成员', {
    confirmButtonText: '邀请',
    cancelButtonText: '取消'
  }).catch(() => ({ value: null }))
  if (!value) return
  try {
    await http.post(`/api/teams/${currentSpace.value.id}/members`, {
      username: value.trim(),
      role: 'MEMBER'
    })
    ElMessage.success('邀请成功')
    loadMembers()
  } catch {
    /* global toast */
  }
}

async function removeMember(userId: number) {
  if (!currentSpace.value) return
  await ElMessageBox.confirm('确定移除该成员？', '移除成员', { type: 'warning' })
  try {
    await http.delete(`/api/teams/${currentSpace.value.id}/members/${userId}`)
    ElMessage.success('已移除')
    loadMembers()
  } catch {
    /* global toast */
  }
}

const teamColors = ['#4F7CFF', '#6366F1', '#22C55E', '#F59E0B', '#EF4444', '#A855F7']

function teamColor(index: number): string {
  return teamColors[index % teamColors.length]
}

onMounted(loadSpaces)
</script>

<template>
  <div class="cd-page">
    <!-- 团队列表 -->
    <el-card v-if="!currentSpace" shadow="never" class="cd-page-card">
        <template #header>
          <div class="cd-card-header">
            <div class="cd-card-title">
              <el-icon :size="16" color="var(--cd-primary)"><UserFilled /></el-icon>
              <span>我的团队空间</span>
            </div>
            <el-button type="primary" @click="createSpace">
              <el-icon><Plus /></el-icon>
              创建团队
            </el-button>
          </div>
        </template>
        <el-empty v-if="!spaces.length" description="还没有加入任何团队空间" />
        <div v-else class="cd-team-grid">
          <div
            v-for="(space, index) in spaces"
            :key="space.id"
            class="cd-team-card"
            @click="enterSpace(space)"
          >
            <div class="cd-team-card-header" :style="{ background: teamColor(index) }">
              <el-icon :size="24" color="#fff"><UserFilled /></el-icon>
            </div>
            <div class="cd-team-card-body">
              <h3 class="cd-team-name">{{ space.name }}</h3>
              <div class="cd-team-meta">
                <el-tag :type="space.myRole === 'OWNER' ? 'warning' : space.myRole === 'ADMIN' ? 'success' : 'info'" size="small" round>
                  {{ space.myRole }}
                </el-tag>
                <span class="cd-team-members">{{ space.memberCount }} 名成员</span>
              </div>
            </div>
          </div>
        </div>
      </el-card>

    <!-- 团队空间文件管理 -->
    <el-card v-else shadow="never" class="cd-page-card">
        <template #header>
          <div class="cd-card-header">
            <div class="cd-breadcrumb">
              <el-button link @click="backToList" class="cd-back-btn">
                <el-icon><ArrowLeft /></el-icon>
              </el-button>
              <span class="cd-space-name">{{ currentSpace.name }}</span>
              <el-button
                v-if="currentFolderId !== currentSpace.rootFolderId"
                link
                type="primary"
                size="small"
                @click="goBack"
              >
                <el-icon><Back /></el-icon>返回根目录
              </el-button>
            </div>
            <el-button @click="loadMembers">
              <el-icon><User /></el-icon>
              成员管理
            </el-button>
          </div>
        </template>

        <el-table v-loading="loading" :data="files" @row-dblclick="enterFolder">
          <el-table-column label="名称" min-width="320">
            <template #default="{ row }">
              <div class="cd-file-name">
                <el-icon :size="22" :color="row.type === 'folder' ? 'var(--cd-file-folder)' : 'var(--cd-file-default)'">
                  <Folder v-if="row.type === 'folder'" />
                  <Document v-else />
                </el-icon>
                <span>{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="120">
            <template #default="{ row }">
              <span class="cd-cell-text">{{ row.type === 'file' ? fmtSize(row.sizeBytes || 0) : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="180">
            <template #default="{ row }">
              <span class="cd-cell-text">{{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="row.type === 'folder'" link type="primary" @click="enterFolder(row)">
                <el-icon><FolderOpened /></el-icon>打开
              </el-button>
              <el-button v-else link type="primary" @click="downloadFile(row)">
                <el-icon><Download /></el-icon>下载
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

    <!-- 成员管理弹窗 -->
    <el-drawer v-model="showMembers" title="成员管理" size="420px">
      <template #header>
        <div class="cd-member-header">
          <span class="cd-notify-title">成员管理</span>
          <el-button
            type="primary"
            size="small"
            @click="inviteMember"
            :disabled="currentSpace && (currentSpace.myRole !== 'OWNER' && currentSpace.myRole !== 'ADMIN')"
          >
            <el-icon><Plus /></el-icon>
            邀请成员
          </el-button>
        </div>
      </template>
      <el-table :data="members">
        <el-table-column label="用户" min-width="120">
          <template #default="{ row }">
            <div class="cd-member-name">
              <el-avatar :size="28" class="cd-member-avatar">
                {{ (row.username || 'U').charAt(0).toUpperCase() }}
              </el-avatar>
              <span>{{ row.username || '用户' + row.userId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'OWNER' ? 'warning' : row.role === 'ADMIN' ? 'success' : 'info'" size="small" round>
              {{ row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="加入时间" prop="joinTime" width="120">
          <template #default="{ row }">{{ new Date(row.joinTime).toLocaleDateString() }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button
              v-if="row.role !== 'OWNER' && currentSpace && (currentSpace.myRole === 'OWNER' || currentSpace.myRole === 'ADMIN')"
              link
              type="danger"
              size="small"
              @click="removeMember(row.userId)"
            >移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
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
}

/* 团队卡片网格 */
.cd-team-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.cd-team-card {
  border: 1px solid var(--cd-border-light);
  border-radius: var(--cd-radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: var(--cd-transition);
}

.cd-team-card:hover {
  border-color: var(--cd-primary-light);
  box-shadow: var(--cd-shadow-md);
  transform: translateY(-2px);
}

.cd-team-card-header {
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.9;
}

.cd-team-card-body {
  padding: 14px;
}

.cd-team-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--cd-text-primary);
  margin: 0 0 8px;
}

.cd-team-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cd-team-members {
  font-size: 12px;
  color: var(--cd-text-secondary);
}

/* 面包屑导航 */
.cd-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cd-back-btn {
  width: 32px !important;
  height: 32px !important;
}

.cd-space-name {
  font-weight: 600;
  font-size: 16px;
  color: var(--cd-text-primary);
}

.cd-file-name {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 500;
}

.cd-cell-text {
  color: var(--cd-text-secondary);
  font-size: 13px;
}

/* 成员管理 */
.cd-member-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.cd-notify-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text-primary);
}

.cd-member-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cd-member-avatar {
  background: var(--cd-primary-gradient) !important;
  color: #fff !important;
  font-weight: 600 !important;
  font-size: 12px !important;
}
</style>
