<script setup lang="ts">
import { ref, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useTransferStore, type TransferTask } from '@/stores/transfer'
import { fileApiUrl, tokenQuery } from '@/api/http'
import EmptyState from '@/components/EmptyState.vue'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'

const transferStore = useTransferStore()
const { tasks } = storeToRefs(transferStore)

const activeTab = ref<'transferring' | 'completed'>('transferring')
const coverFallback = ref<Record<string, boolean>>({})

const clearAllDialogVisible = ref(false)
const cancelTaskDialogVisible = ref(false)
const taskToOperate = ref<TransferTask | null>(null)

function triggerCancelTask(task: TransferTask) {
  taskToOperate.value = task
  cancelTaskDialogVisible.value = true
}

async function handleCancelConfirm() {
  if (!taskToOperate.value) return
  try {
    await transferStore.cancelTask(taskToOperate.value.id)
    uni.showToast({
      title: taskToOperate.value.status === 'done' || taskToOperate.value.status === 'instant' || taskToOperate.value.status === 'error'
        ? '已删除记录' 
        : '已取消传输',
      icon: 'success'
    })
  } catch {
    /* handled */
  } finally {
    taskToOperate.value = null
  }
}

function triggerClearCompleted() {
  clearAllDialogVisible.value = true
}

async function handleClearCompletedConfirm() {
  try {
    await transferStore.clearCompleted()
    uni.showToast({ title: '已清除完成记录', icon: 'success' })
  } catch {
    /* handled */
  }
}

const transferringTasks = computed(() =>
  tasks.value.filter((t) => t.status !== 'done' && t.status !== 'instant' && t.status !== 'error')
)

const completedTasks = computed(() =>
  tasks.value.filter((t) => t.status === 'done' || t.status === 'instant' || t.status === 'error')
)

function fmtSize(bytes?: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

interface FileIconInfo {
  emoji: string
  bg: string
}

function getFileIconInfo(name: string): FileIconInfo {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext))
    return { emoji: '📦', bg: 'rgba(139,146,165,0.12)' }
  if (['mp4', 'mkv', 'avi', 'mov', 'flv', 'webm'].includes(ext))
    return { emoji: '🎬', bg: 'rgba(168,85,247,0.12)' }
  if (['mp3', 'wav', 'flac', 'aac', 'ogg'].includes(ext))
    return { emoji: '🎵', bg: 'rgba(236,72,153,0.12)' }
  if (['pdf'].includes(ext))
    return { emoji: '📄', bg: 'rgba(239,68,68,0.1)' }
  if (['doc', 'docx'].includes(ext))
    return { emoji: '📝', bg: 'rgba(59,130,246,0.1)' }
  if (['xls', 'xlsx'].includes(ext))
    return { emoji: '📊', bg: 'rgba(34,197,94,0.1)' }
  if (['ppt', 'pptx'].includes(ext))
    return { emoji: '📽️', bg: 'rgba(249,115,22,0.1)' }
  if (['html', 'css', 'js', 'ts', 'vue', 'json'].includes(ext))
    return { emoji: '💻', bg: 'rgba(99,102,241,0.1)' }
  if (['txt', 'md', 'log'].includes(ext))
    return { emoji: '📃', bg: 'rgba(107,114,128,0.1)' }
  if (['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'bmp'].includes(ext))
    return { emoji: '🖼️', bg: 'rgba(20,184,166,0.1)' }
  return { emoji: '📁', bg: 'rgba(100,116,139,0.08)' }
}

function formatPercent(progress: number): string {
  return `${Math.round(progress * 100)}%`
}

const imageExts = ['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'bmp']
const videoExts = ['mp4', 'mkv', 'avi', 'mov', 'flv', 'webm']

function isImageName(name: string): boolean {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return imageExts.includes(ext)
}

function isVideoName(name: string): boolean {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return videoExts.includes(ext)
}

function taskHasCover(t: TransferTask): boolean {
  return !!t.coverUrl || (!!t.fileId && (isImageName(t.name) || isVideoName(t.name)))
}

function coverSrc(t: TransferTask): string {
  if (t.coverUrl) return t.coverUrl
  if (!t.fileId) return ''
  const param = `access_token=${tokenQuery()}`
  if (isVideoName(t.name) || coverFallback.value[t.id]) {
    return fileApiUrl(`/api/files/${t.fileId}/preview?${param}`)
  }
  return fileApiUrl(`/api/files/${t.fileId}/thumbnail?${param}`)
}

function onCoverError(t: TransferTask) {
  if (t.fileId && !coverFallback.value[t.id]) {
    coverFallback.value[t.id] = true
  }
}
</script>

<template>
  <view class="transfer-page">
    <!-- 自定义Header -->
    <view class="page-header-wrap">
      <view class="page-header">
        <view class="header-glow" />
        <view class="header-bar">
          <view class="header-center">
            <text class="header-title">传输列表</text>
            <text class="header-sub">文件上传与下载进度</text>
          </view>
          <view class="header-right">
            <view
              v-if="completedTasks.length && activeTab === 'completed'"
              class="clear-btn cd-pressable"
              @click="triggerClearCompleted"
            >
              <text class="clear-text">清除</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- Tab 切换 -->
    <view class="tab-section">
      <view class="tab-bar">
        <view
          class="tab-item cd-pressable"
          :class="{ active: activeTab === 'transferring' }"
          @click="activeTab = 'transferring'"
        >
          <text class="tab-label">传输中</text>
          <view class="tab-count" :class="{ active: activeTab === 'transferring' }">
            <text>{{ transferringTasks.length }}</text>
          </view>
        </view>
        <view
          class="tab-item cd-pressable"
          :class="{ active: activeTab === 'completed' }"
          @click="activeTab = 'completed'"
        >
          <text class="tab-label">已完成</text>
          <view class="tab-count" :class="{ active: activeTab === 'completed' }">
            <text>{{ completedTasks.length }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 列表区域 -->
    <scroll-view scroll-y class="task-scroll" :show-scrollbar="false">
      <!-- 传输中 -->
      <view v-if="activeTab === 'transferring'" class="task-list">
        <EmptyState
          v-if="!transferringTasks.length"
          icon="download"
          title="暂无传输任务"
          description="开始上传或下载文件后将在此显示"
        />

        <view
          v-for="t in transferringTasks"
          :key="t.id"
          class="task-card"
          :class="{ paused: t.status === 'paused', error: t.status === 'error' }"
        >
          <!-- 左侧封面区 -->
          <view class="task-icon-area">
            <view
              class="task-icon-box"
              :class="{ 'has-cover': taskHasCover(t) }"
              :style="{ background: getFileIconInfo(t.name).bg }"
            >
              <image
                v-if="taskHasCover(t)"
                :src="coverSrc(t)"
                class="task-cover-media"
                mode="aspectFill"
                @error="onCoverError(t)"
              />
              <text v-else class="task-emoji">{{ getFileIconInfo(t.name).emoji }}</text>
            </view>
            <!-- 传输类型小标记 -->
            <view class="task-type-dot" :class="t.type">
              <text class="type-arrow">{{ t.type === 'upload' ? '↑' : '↓' }}</text>
            </view>
          </view>

          <!-- 内容区 -->
          <view class="task-content">
            <text class="task-name" :title="t.name">{{ t.name }}</text>
            
            <!-- 进度条 -->
            <view class="progress-section">
              <view class="progress-track">
                <view
                  class="progress-fill"
                  :class="{ error: t.status === 'error', paused: t.status === 'paused' }"
                  :style="{
                    width: `${Math.round(t.progress * 100)}%`,
                    background: t.status === 'error'
                      ? '#EF4444'
                      : t.status === 'paused'
                      ? '#F59E0B'
                      : 'linear-gradient(90deg, #010710 0%, #1e293b 60%, #4f46e5 100%)'
                  }"
                />
              </view>
              <text class="progress-text" :class="{ error: t.status === 'error' }">
                {{ formatPercent(t.progress) }}
              </text>
            </view>

            <!-- 元信息 -->
            <view class="task-meta">
              <view class="meta-group">
                <text class="meta-size">{{ fmtSize(t.loaded) }} / {{ fmtSize(t.size) }}</text>
              </view>
              <text class="meta-speed" :class="{ error: t.status === 'error' }">{{ t.speed }}</text>
            </view>
          </view>

          <!-- 操作区 -->
          <view class="task-actions">
            <template v-if="t.type === 'upload'">
              <view
                v-if="t.status === 'running'"
                class="action-circle pause"
                @click="transferStore.pauseUpload(t.id)"
              >
                <text class="action-icon">⏸</text>
              </view>
              <view
                v-else-if="t.status === 'paused'"
                class="action-circle resume"
                @click="transferStore.resumeUpload(t.id)"
              >
                <text class="action-icon">▶</text>
              </view>
            </template>
            <view class="action-circle cancel" @click="triggerCancelTask(t)">
              <text class="action-icon">✕</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 已完成 -->
      <view v-else class="task-list">
        <EmptyState
          v-if="!completedTasks.length"
          icon="checkmark-circle-fill"
          title="暂无完成记录"
          description="传输完成的任务将在此显示"
        />

        <view
          v-for="t in completedTasks"
          :key="t.id"
          class="task-card task-card--done"
          :class="{ error: t.status === 'error' }"
        >
          <view class="task-icon-area">
            <view
              class="task-icon-box small"
              :class="{ 'has-cover': t.status !== 'error' && taskHasCover(t) }"
              :style="{ background: t.status === 'error' ? 'rgba(239,68,68,0.08)' : getFileIconInfo(t.name).bg }"
            >
              <image
                v-if="t.status !== 'error' && taskHasCover(t)"
                :src="coverSrc(t)"
                class="task-cover-media"
                mode="aspectFill"
                @error="onCoverError(t)"
              />
              <text v-else class="task-emoji">{{ t.status === 'error' ? '❌' : getFileIconInfo(t.name).emoji }}</text>
            </view>
          </view>

          <view class="task-info task-info--done">
            <view class="done-title-row">
              <text class="task-name done-name">{{ t.name }}</text>
            </view>
            <view class="done-sub-row">
              <text class="meta-size">{{ fmtSize(t.size) }}</text>
              <text class="meta-divider">·</text>
              <text class="meta-type">{{ t.type === 'upload' ? '上传' : '下载' }}</text>
              <view class="status-chip" :class="{
                success: t.status === 'done',
                instant: t.status === 'instant',
                fail: t.status === 'error'
              }">
                <text v-if="t.status === 'instant'">秒传</text>
                <text v-else-if="t.status === 'done'">已完成</text>
                <text v-else>失败</text>
              </view>
            </view>
          </view>

          <view class="done-remove-btn" @click="triggerCancelTask(t)">
            <svg viewBox="0 0 24 24" fill="none" class="remove-svg">
              <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="3" stroke-linecap="round" />
            </svg>
          </view>
        </view>
      </view>

      <!-- 底部安全区 -->
      <view class="bottom-safe" />
    </scroll-view>

    <!-- Clear Completed Confirm Dialog -->
    <MobileConfirmDialog
      v-model:show="clearAllDialogVisible"
      title="清除记录"
      message="确定清除所有已完成的传输记录吗？"
      confirm-text="清除"
      danger
      @confirm="handleClearCompletedConfirm"
    />

    <!-- Cancel/Delete Task Confirm Dialog -->
    <MobileConfirmDialog
      v-model:show="cancelTaskDialogVisible"
      title="确认操作"
      :message="taskToOperate ? (taskToOperate.status === 'done' || taskToOperate.status === 'instant' || taskToOperate.status === 'error' ? `确定要删除完成记录「${taskToOperate.name}」吗？` : `确定要取消文件「${taskToOperate.name}」的传输吗？`) : ''"
      confirm-text="确定"
      danger
      @confirm="handleCancelConfirm"
    />
  </view>
</template>

<style scoped lang="scss">
.transfer-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--cd-bg, #f5f6fa);
  overflow: hidden;
}

/* ==========================================
   Header
   ========================================== */
.page-header-wrap {
  padding: calc(var(--status-bar-height, 0rpx) + 20rpx) 24rpx 0;
  background: var(--cd-bg, #f5f6fa);
}

.page-header {
  position: relative;
  overflow: hidden;
  padding: 28rpx 28rpx 28rpx;
  border-radius: 32rpx;
  background: var(--cd-accent-surface);
  backdrop-filter: blur(24rpx);
  -webkit-backdrop-filter: blur(24rpx);
  border: 1rpx solid var(--cd-accent-border);
  box-shadow: var(--cd-accent-shadow);
}

.header-glow {
  display: none;
}

.header-bar {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
}

.back-btn {
  width: 68rpx;
  height: 68rpx;
  border-radius: 999rpx;
  background: #f1f5f9;
  border: 1rpx solid var(--cd-border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s ease;

  &:active {
    background: #e2e8f0;
    transform: scale(0.88);
  }
}

.header-center {
  flex: 1;
  min-width: 0;
}

.header-title {
  display: block;
  font-size: 38rpx;
  font-weight: 800;
  color: var(--cd-text);
}

.header-sub {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--cd-text-secondary);
}

.header-right {
  flex-shrink: 0;
}

.clear-btn {
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: #f1f5f9;
  border: 1rpx solid var(--cd-border);
  transition: all 0.2s ease;

  &:active {
    background: #e2e8f0;
    transform: scale(0.95);
  }
}

.clear-text {
  font-size: 22rpx;
  font-weight: 700;
  color: var(--cd-text);
}

/* ==========================================
   Tab 切换
   ========================================== */
.tab-section {
  padding: 0 24rpx;
  margin-top: 20rpx;
  position: relative;
  z-index: 2;
}

.tab-bar {
  display: flex;
  background: #fff;
  border-radius: 24rpx;
  padding: 8rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.06);
}

.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  padding: 20rpx 0;
  border-radius: 18rpx;
  position: relative;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);

  &.active {
    background: linear-gradient(135deg, #010710 0%, #1e293b 100%);
    box-shadow: 0 4rpx 16rpx rgba(1, 7, 16, 0.2);
  }

  .tab-label {
    font-size: 26rpx;
    font-weight: 600;
    color: #94a3b8;
    transition: color 0.25s ease;
  }

  &.active .tab-label {
    color: #fff;
    font-weight: 700;
  }
}

.tab-count {
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background: rgba(148, 163, 184, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.25s ease;

  text {
    font-size: 20rpx;
    font-weight: 700;
    color: #64748b;
    transition: color 0.25s ease;
  }

  &.active {
    background: rgba(255, 255, 255, 0.15);

    text {
      color: #fff;
    }
  }
}

/* ==========================================
   滚动列表
   ========================================== */
.task-scroll {
  flex: 1;
  min-height: 0;
}

.task-list {
  padding: 20rpx 24rpx 0;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.bottom-safe {
  height: calc(env(safe-area-inset-bottom) + 40rpx);
}

/* ==========================================
   任务卡片
   ========================================== */
.task-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  background: #fff;
  border-radius: 24rpx;
  border: 1rpx solid rgba(0, 0, 0, 0.04);
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
  transition: all 0.2s ease;

  &.paused {
    border-color: rgba(245, 158, 11, 0.12);
    background: rgba(255, 251, 235, 0.5);
  }

  &.error {
    border-color: rgba(239, 68, 68, 0.1);
    background: rgba(254, 242, 242, 0.4);
  }

  &.task-card--done {
    align-items: center;
    padding: 24rpx;
    gap: 16rpx;
    background: rgba(255, 255, 255, 0.7);
  }

  &.task-card--done.error {
    border-color: rgba(239, 68, 68, 0.15);
    background: rgba(254, 242, 242, 0.45);
  }
}

/* 图标区域 */
.task-icon-area {
  position: relative;
  flex-shrink: 0;
}

.task-icon-box {
  width: 88rpx;
  height: 88rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  &.small {
    width: 72rpx;
    height: 72rpx;
    border-radius: 18rpx;
  }

  &.has-cover {
    background: transparent !important;
    border: 1rpx solid var(--cd-border-light, #e2e8f0);
    border-radius: 14rpx;
  }
}

.task-emoji {
  font-size: 40rpx;
  line-height: 1;
}

.task-cover-media {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: inherit;
}

.task-type-dot {
  position: absolute;
  bottom: -4rpx;
  right: -4rpx;
  width: 34rpx;
  height: 34rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3rpx solid #fff;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.1);

  &.upload {
    background: linear-gradient(135deg, #4f46e5, #6366f1);
  }

  &.download {
    background: linear-gradient(135deg, #f59e0b, #f97316);
  }
}

.type-arrow {
  font-size: 18rpx;
  font-weight: 800;
  color: #fff;
  line-height: 1;
}

/* 内容区 */
.task-content {
  flex: 1;
  min-width: 0;
}

.task-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.task-info--done {
  gap: 4rpx;
}

.done-title-row {
  min-width: 0;
}

.done-name {
  margin-bottom: 0;
}

.done-sub-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6rpx;
  min-height: 28rpx;
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1;
}

.done-sub-row .status-chip {
  margin-left: 2rpx;
}

.meta-divider {
  opacity: 0.6;
}

.task-name {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
  color: var(--cd-text, #1e293b);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 进度条 */
.progress-section {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin: 14rpx 0 10rpx;
}

.progress-track {
  flex: 1;
  height: 8rpx;
  border-radius: 999rpx;
  background: #f1f5f9;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999rpx;
  transition: width 0.3s ease;
  position: relative;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(90deg, transparent, rgba(255,255,255,0.3), transparent);
    animation: shimmer 2s ease-in-out infinite;
  }

  &.error::after,
  &.paused::after {
    display: none;
  }
}

@keyframes shimmer {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

.progress-text {
  font-size: 22rpx;
  font-weight: 700;
  color: var(--cd-primary, #1e293b);
  font-variant-numeric: tabular-nums;
  width: 66rpx;
  text-align: right;
  flex-shrink: 0;

  &.error {
    color: #ef4444;
  }
}

/* 元信息 */
.task-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.meta-group {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.meta-size {
  font-size: 22rpx;
  color: #94a3b8;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.meta-speed {
  font-size: 22rpx;
  font-weight: 700;
  color: #1e293b;
  font-variant-numeric: tabular-nums;

  &.error {
    color: #ef4444;
  }
}

/* 状态标签 */
.status-chip {
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 700;
  letter-spacing: 0.5rpx;

  &.success {
    background: rgba(34, 197, 94, 0.08);
    color: #16a34a;
  }

  &.instant {
    background: rgba(99, 102, 241, 0.08);
    color: #4f46e5;
  }

  &.fail {
    background: rgba(239, 68, 68, 0.08);
    color: #dc2626;
  }
}

/* 操作按钮 */
.task-actions {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  flex-shrink: 0;
}

.action-circle {
  width: 56rpx;
  height: 56rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  background: #f8fafc;
  border: 1rpx solid #e2e8f0;

  .action-icon {
    font-size: 22rpx;
    line-height: 1;
  }

  &.pause {
    background: rgba(245, 158, 11, 0.08);
    border-color: rgba(245, 158, 11, 0.15);
    color: #d97706;
  }

  &.resume {
    background: rgba(79, 70, 229, 0.08);
    border-color: rgba(79, 70, 229, 0.15);
    color: #4f46e5;
  }

  &.cancel {
    background: transparent;
    border-color: transparent;
    color: #94a3b8;
  }

  &:active {
    transform: scale(0.9);
  }
}

/* 完成列表删除按钮（匹配 PC 风格） */
.done-remove-btn {
  width: 48rpx;
  height: 48rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #1e293b;
  transition: background 0.15s ease, color 0.15s ease;

  &:active {
    background: rgba(148, 163, 184, 0.12);
    color: #64748b;
  }
}

.remove-svg {
  width: 28rpx;
  height: 28rpx;
}
</style>
