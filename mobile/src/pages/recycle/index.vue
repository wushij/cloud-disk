<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { request } from '@/api/http'
import MobileHeader from '@/components/MobileHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import FolderTypeIcon from '@/components/FolderTypeIcon.vue'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'
import { fmtSize, fileCoverUrl, fileHasCover, fileIsVideoCover } from '@/utils/fileCover'
import { fileExtLabel, fileTypeColor, fileTypeIcon, fileTypeKind } from '@/utils/fileType'
import CachedCover from '@/components/CachedCover.vue'

interface RecycleItem {
  id: number
  name: string
  type: 'file' | 'folder'
  sizeBytes?: number
  deletedAt?: string
  mimeType?: string | null
  hasThumbnail?: boolean
}

const auth = useAuthStore()
const list = ref<RecycleItem[]>([])
const loading = ref(false)
const listInitialized = ref(false)

const restoreDialogVisible = ref(false)
const itemToRestore = ref<RecycleItem | null>(null)

const purgeDialogVisible = ref(false)
const itemToPurge = ref<RecycleItem | null>(null)

const clearAllDialogVisible = ref(false)
const coverBroken = ref<Record<string, boolean>>({})

function coverKey(item: RecycleItem) {
  return `${item.type}-${item.id}`
}

async function loadList() {
  loading.value = true
  try {
    const data = await request<{ content?: RecycleItem[] } | RecycleItem[]>({ url: '/api/recycle' })
    list.value = Array.isArray(data) ? data : data.content || []
    listInitialized.value = true
  } catch {
    /* handled */
  } finally {
    loading.value = false
  }
}

onShow(async () => {
  if (!auth.requireLogin()) return
  if (!listInitialized.value) {
    await loadList()
  }
})

function formatDate(d?: string) {
  if (!d) return ''
  const dt = new Date(d)
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const day = String(dt.getDate()).padStart(2, '0')
  const h = String(dt.getHours()).padStart(2, '0')
  const min = String(dt.getMinutes()).padStart(2, '0')
  return `删除于 ${dt.getFullYear()}/${m}/${day} ${h}:${min}`
}

function restoreItem(item: RecycleItem) {
  itemToRestore.value = item
  restoreDialogVisible.value = true
}

async function handleRestoreConfirm() {
  const item = itemToRestore.value
  if (!item) return
  const url =
    item.type === 'folder'
      ? `/api/recycle/restore/folder/${item.id}`
      : `/api/recycle/restore/file/${item.id}`
  try {
    await request({ url, method: 'POST' })
    uni.showToast({ title: '已恢复', icon: 'success' })
    await loadList()
  } catch {
    /* handled */
  } finally {
    itemToRestore.value = null
  }
}

function purgeItem(item: RecycleItem) {
  itemToPurge.value = item
  purgeDialogVisible.value = true
}

async function handlePurgeConfirm() {
  const item = itemToPurge.value
  if (!item) return
  const url =
    item.type === 'folder' ? `/api/recycle/folder/${item.id}` : `/api/recycle/file/${item.id}`
  try {
    await request({ url, method: 'DELETE' })
    uni.showToast({ title: '已删除', icon: 'success' })
    await loadList()
  } catch {
    /* handled */
  } finally {
    itemToPurge.value = null
  }
}

function confirmClearAll() {
  clearAllDialogVisible.value = true
}

async function handleClearAllConfirm() {
  try {
    await request({ url: '/api/recycle/clear', method: 'DELETE' })
    uni.showToast({ title: '回收站已清空', icon: 'success' })
    await loadList()
  } catch {
    /* handled */
  }
}
</script>

<template>
  <view class="page">
    <MobileHeader
      title="回收站"
      :subtitle="list.length ? `${list.length} 项` : ''"
      caption="已删除的文件与文件夹可在此恢复，或彻底删除释放空间"
      gradient
      icon-type="recycle"
    >
      <template #right>
        <view v-if="list.length" class="header-action-btn danger cd-pressable" @click="confirmClearAll">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <polyline points="3 6 5 6 21 6" />
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          </svg>
          <text class="header-action-text">清空</text>
        </view>
      </template>
    </MobileHeader>

    <scroll-view scroll-y class="scroll">
      <view v-if="loading" class="state-box">
        <u-loading-icon text="加载中..." color="var(--cd-primary)" />
      </view>
      <view v-else-if="!list.length" class="state-box">
        <EmptyState
          icon="trash"
          title="回收站是空的"
          description="已删除的文件可暂存在这里，可随时找回"
        />
      </view>
      <view v-else class="list-wrapper">
        <view v-for="item in list" :key="`${item.type}-${item.id}`" class="recycle-card">
          <!-- 封面或图标展示区域 -->
          <view class="recycle-thumb" :class="{ cover: fileHasCover(item as any), folder: item.type === 'folder' || fileTypeKind(item as any) === 'archive' }">
            <template v-if="fileHasCover(item as any) && !coverBroken[coverKey(item)]">
              <CachedCover
                :file-id="item.id"
                :src="fileCoverUrl(item as any)"
                :has-thumbnail="item.hasThumbnail"
                class="recycle-cover"
                @error="coverBroken[coverKey(item)] = true"
              />
              <view v-if="fileIsVideoCover(item as any)" class="recycle-play-badge">
                <u-icon name="play-circle-fill" size="18" color="#fff" />
              </view>
            </template>
            <view v-else class="recycle-file-icon" :class="fileTypeKind(item as any)">
              <FolderTypeIcon
                v-if="item.type === 'folder'"
                :size="36"
              />
              <FolderTypeIcon
                v-else-if="fileTypeKind(item as any) === 'archive'"
                archive
                :size="36"
              />
              <template v-else>
                <u-icon
                  :name="fileTypeIcon(item as any)"
                  size="24"
                  :color="fileTypeColor(item as any)"
                />
                <text class="recycle-ext">{{ fileExtLabel(item as any) }}</text>
              </template>
            </view>
          </view>

          <!-- 主文本区域 -->
          <view class="recycle-main">
            <text class="name">{{ item.name }}</text>
            <view class="recycle-meta-row">
              <text class="meta">{{ item.type === 'folder' ? '文件夹' : fmtSize(item.sizeBytes || 0) }}</text>
              <text v-if="item.deletedAt" class="delete-date">{{ formatDate(item.deletedAt) }}</text>
            </view>
          </view>

          <!-- 操作按钮区域（左右横排） -->
          <view class="actions">
            <view class="btn restore cd-pressable" @click="restoreItem(item)">
              <text>恢复</text>
            </view>
            <view class="btn purge cd-pressable" @click="purgeItem(item)">
              <text>删除</text>
            </view>
          </view>
        </view>
      </view>
      <view v-if="list.length" class="scroll-bottom-spacer" />
    </scroll-view>

    <MobileConfirmDialog
      v-model:show="purgeDialogVisible"
      title="彻底删除"
      :message="itemToPurge ? `确定彻底删除「${itemToPurge.name}」？此操作无法撤销！` : ''"
      confirm-text="确定"
      danger
      @confirm="handlePurgeConfirm"
    />

    <MobileConfirmDialog
      v-model:show="clearAllDialogVisible"
      title="清空回收站"
      message="确定要清空回收站中的所有项目吗？此操作将永久删除且无法撤销！"
      confirm-text="清空"
      danger
      @confirm="handleClearAllConfirm"
    />

    <MobileConfirmDialog
      v-model:show="restoreDialogVisible"
      title="恢复项目"
      :message="itemToRestore ? `确定要恢复「${itemToRestore.name}」？` : ''"
      confirm-text="恢复"
      @confirm="handleRestoreConfirm"
    />
  </view>
</template>

<style scoped lang="scss">
.page {
  height: 100vh;
  overflow: hidden;
  padding-bottom: calc(env(safe-area-inset-bottom) + 24rpx);
  background: var(--cd-bg);
}

.scroll {
  height: calc(100vh - 200rpx);
  padding: 4rpx 0 24rpx;
}

.scroll-bottom-spacer {
  height: calc(env(safe-area-inset-bottom) + 32rpx);
}

.header-action-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 20rpx;
  border-radius: var(--cd-radius-full);
  background: rgba(79, 124, 255, 0.05);
  border: 1rpx solid rgba(79, 124, 255, 0.12);
  transition: all var(--cd-transition-fast);

  &.danger {
    background: rgba(239, 68, 68, 0.05);
    border-color: rgba(239, 68, 68, 0.12);

    .header-action-text,
    svg {
      color: #ef4444;
    }

    &:active {
      background: rgba(239, 68, 68, 0.12);
    }
  }

  &:active {
    opacity: 0.85;
  }
}

.header-action-text {
  font-size: 22rpx;
  font-weight: 700;
  color: #ef4444;
}

.list-wrapper {
  padding-top: 8rpx;
  padding-bottom: 32rpx;
}

.recycle-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin: 0 24rpx 14rpx;
  padding: 24rpx;
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-lg);
  box-shadow: var(--cd-shadow-card);
  border: 1rpx solid var(--cd-border-light);
  transition: all var(--cd-transition-bounce);
  
  &:active {
    transform: scale(0.99);
  }
}

/* ============ 缩略图/图标 ============ */
.recycle-thumb {
  width: 88rpx;
  height: 88rpx;
  border-radius: var(--cd-radius);
  overflow: hidden;
  flex-shrink: 0;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
}

.recycle-thumb.folder {
  background: transparent;
}

.recycle-thumb.cover {
  position: relative;
  box-shadow: inset 0 0 0 1rpx rgba(0, 0, 0, 0.03);
}

.recycle-cover {
  width: 100%;
  height: 100%;
}

.recycle-video-wrap {
  position: relative;
  width: 100%;
  height: 100%;
}

.recycle-play-badge {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(2rpx);
}

.recycle-file-icon {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3rpx;
  background: linear-gradient(145deg, rgba(1, 7, 16, 0.04), rgba(15, 26, 46, 0.02));
}

.recycle-file-icon.folder {
  background: transparent;
}

.recycle-ext {
  font-size: 16rpx;
  font-weight: 700;
  color: var(--cd-text-muted);
  letter-spacing: 0.5rpx;
}

/* ============ 主文本 ============ */
.recycle-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--cd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recycle-meta-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-wrap: wrap;
}

.meta {
  font-size: 22rpx;
  color: var(--cd-text-muted);
}

.delete-date {
  font-size: 22rpx;
  color: var(--cd-text-muted);
  opacity: 0.75;
}

/* ============ 操作按钮 ============ */
.actions {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-shrink: 0;
}

.btn {
  padding: 10rpx 24rpx;
  border-radius: var(--cd-radius-full);
  font-size: 22rpx;
  font-weight: 600;
  text-align: center;
  transition: all var(--cd-transition-fast);
}

.btn:active {
  transform: scale(0.92);
  opacity: 0.85;
}

.btn.restore {
  background: var(--cd-primary-muted);
  color: var(--cd-primary);
  &:active {
    background: var(--cd-primary-muted-strong);
  }
}

.btn.purge {
  background: var(--cd-danger-bg);
  color: var(--cd-danger);
  &:active {
    background: rgba(239, 68, 68, 0.14);
  }
}

.state-box {
  padding: 140rpx 0;
  display: flex;
  justify-content: center;
}
</style>
