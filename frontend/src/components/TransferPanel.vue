<script setup lang="ts">
import { ref, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useTransferStore, type TransferTask } from '@/stores/transfer'
import { fmtSize } from '@/utils/fileMeta'
import { TOKEN_KEY } from '@/api/http'
import { ElMessage } from 'element-plus'
import { useConfirmDialogStore } from '@/stores/confirmDialog'

const transferStore = useTransferStore()
const confirmDialog = useConfirmDialogStore()
const { tasks, isCollapsed, runningCount } = storeToRefs(transferStore)
const activeTab = ref<'transferring' | 'completed'>('transferring')
const coverFallback = ref<Record<string, boolean>>({})

const transferringTasks = computed(() =>
  tasks.value.filter((t) => t.status !== 'done' && t.status !== 'instant' && t.status !== 'error')
)

const completedTasks = computed(() =>
  tasks.value.filter((t) => t.status === 'done' || t.status === 'instant' || t.status === 'error')
)

interface FileIconInfo {
  icon: string
  bg: string
}

const imageExts = ['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'bmp']
const videoExts = ['mp4', 'mkv', 'avi', 'mov', 'flv', 'webm']

function fileExt(name: string): string {
  return name.split('.').pop()?.toLowerCase() || ''
}

function isImageName(name: string): boolean {
  return imageExts.includes(fileExt(name))
}

function isVideoName(name: string): boolean {
  return videoExts.includes(fileExt(name))
}

function getFileIconInfo(name: string): FileIconInfo {
  const ext = fileExt(name)
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext))
    return { icon: '📦', bg: 'rgba(139,146,165,0.12)' }
  if (videoExts.includes(ext)) return { icon: '🎬', bg: 'rgba(168,85,247,0.12)' }
  if (['mp3', 'wav', 'flac', 'aac', 'ogg'].includes(ext))
    return { icon: '🎵', bg: 'rgba(236,72,153,0.12)' }
  if (ext === 'pdf') return { icon: '📄', bg: 'rgba(239,68,68,0.12)' }
  if (['doc', 'docx'].includes(ext)) return { icon: '📝', bg: 'rgba(59,130,246,0.12)' }
  if (['xls', 'xlsx'].includes(ext)) return { icon: '📊', bg: 'rgba(34,197,94,0.12)' }
  if (['ppt', 'pptx'].includes(ext)) return { icon: '📽️', bg: 'rgba(249,115,22,0.12)' }
  if (['txt', 'md', 'log'].includes(ext)) return { icon: '📃', bg: 'rgba(107,114,128,0.12)' }
  if (imageExts.includes(ext)) return { icon: '🖼️', bg: 'rgba(20,184,166,0.12)' }
  if (['html', 'css', 'js', 'ts', 'vue', 'json', 'xml'].includes(ext))
    return { icon: '💻', bg: 'rgba(99,102,241,0.12)' }
  return { icon: '📁', bg: 'rgba(100,116,139,0.1)' }
}

function tokenParam() {
  return encodeURIComponent(localStorage.getItem(TOKEN_KEY) || '')
}

function taskCoverKind(t: TransferTask): 'image' | 'video' | null {
  if (t.coverUrl) return 'image'
  if (t.fileObj?.type.startsWith('image/')) return 'image'
  if (!t.fileId) return null
  if (isVideoName(t.name)) return 'video'
  if (isImageName(t.name)) return 'image'
  return null
}

function taskHasCover(t: TransferTask): boolean {
  return taskCoverKind(t) !== null
}

function coverSrc(t: TransferTask): string {
  if (t.coverUrl) return t.coverUrl
  if (!t.fileId) return ''
  const kind = taskCoverKind(t)
  if (kind === 'video' || coverFallback.value[t.id]) {
    return `/api/files/${t.fileId}/preview?access_token=${tokenParam()}`
  }
  return `/api/files/${t.fileId}/thumbnail?access_token=${tokenParam()}`
}

function onCoverError(t: TransferTask) {
  if (t.fileId && !coverFallback.value[t.id]) {
    coverFallback.value[t.id] = true
  }
}

function formatPercent(progress: number): string {
  return `${Math.round(progress * 100)}%`
}

function progressGradient(status: string): string {
  if (status === 'error') return '#EF4444'
  if (status === 'paused') return '#F59E0B'
  return 'var(--cd-primary-gradient)'
}

function handleCollapseToggle() {
  transferStore.toggleCollapse()
}

async function handleCancelTask(task: TransferTask) {
  const isCompleted = task.status === 'done' || task.status === 'instant' || task.status === 'error'
  const confirmMsg = isCompleted
    ? `确定要删除完成记录「${task.name}」吗？`
    : `确定要取消文件「${task.name}」的传输吗？`

  const ok = await confirmDialog.open({
    title: '提示',
    message: confirmMsg,
    confirmText: '确定',
    danger: isCompleted
  })
  if (!ok) return

  try {
    await transferStore.cancelTask(task.id)
    ElMessage.success(isCompleted ? '已删除记录' : '已取消传输')
  } catch (e) {
    console.error(e)
  }
}

async function handleClearCompleted() {
  const ok = await confirmDialog.open({
    title: '提示',
    message: '确定要清除所有已完成的传输记录吗？',
    confirmText: '清除',
    danger: true
  })
  if (!ok) return

  try {
    await transferStore.clearCompleted()
    ElMessage.success('已清除已完成记录')
  } catch (e) {
    console.error(e)
  }
}
</script>

<template>
  <div v-if="tasks.length" class="cd-transfer-container" :class="{ 'is-collapsed': isCollapsed }">
    <!-- 折叠胶囊 -->
    <div v-if="isCollapsed" class="cd-transfer-mini" @click="handleCollapseToggle">
      <div class="mini-inner" :class="{ pulse: runningCount > 0 }">
        <svg class="mini-icon" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" />
          <path d="M6.5 11L9 8.5L11.5 11M9 8.5V16.5" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M12.5 13.5L15 16L17.5 13.5M15 8V16" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <span class="mini-text">
        {{ runningCount > 0 ? `${runningCount} 个任务进行中` : '传输列表' }}
      </span>
      <span v-if="runningCount > 0" class="mini-badge">{{ runningCount }}</span>
    </div>

    <!-- 主面板 -->
    <div v-else class="cd-transfer-panel">
      <div class="panel-header">
        <div class="header-left">
          <span class="header-title">传输列表</span>
          <span class="header-sub">文件上传与下载进度</span>
        </div>
        <div class="header-actions">
          <button
            v-if="completedTasks.length && activeTab === 'completed'"
            class="header-text-btn"
            @click="handleClearCompleted"
          >
            清除
          </button>
          <button class="header-icon-btn" title="最小化" @click="handleCollapseToggle">
            <svg viewBox="0 0 24 24" fill="none" class="action-svg">
              <path d="M19 13H5v-2h14v2z" fill="currentColor" />
            </svg>
          </button>
        </div>
      </div>

      <div class="panel-tabs">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'transferring' }"
          @click="activeTab = 'transferring'"
        >
          <span class="tab-label">传输中</span>
          <span class="tab-count">{{ transferringTasks.length }}</span>
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'completed' }"
          @click="activeTab = 'completed'"
        >
          <span class="tab-label">已完成</span>
          <span class="tab-count">{{ completedTasks.length }}</span>
        </button>
      </div>

      <div class="panel-body">
        <!-- 传输中 -->
        <div v-if="activeTab === 'transferring'" class="task-list">
          <div v-if="!transferringTasks.length" class="empty-state">
            <p>暂无传输中的任务</p>
            <span>开始上传或下载后将在此显示</span>
          </div>

          <div
            v-for="t in transferringTasks"
            :key="t.id"
            class="task-card"
            :class="{ error: t.status === 'error', paused: t.status === 'paused' }"
          >
            <div class="task-icon-area">
              <div
                class="task-icon-box"
                :class="{ 'has-cover': taskHasCover(t) }"
                :style="{ background: getFileIconInfo(t.name).bg }"
              >
                <img
                  v-if="taskCoverKind(t) === 'image'"
                  :src="coverSrc(t)"
                  class="task-cover-media"
                  alt=""
                  @error="onCoverError(t)"
                />
                <video
                  v-else-if="taskCoverKind(t) === 'video'"
                  :src="coverSrc(t)"
                  class="task-cover-media"
                  muted
                  preload="metadata"
                />
                <span v-else class="task-emoji">{{ getFileIconInfo(t.name).icon }}</span>
              </div>
              <span class="task-type-badge" :class="t.type" :title="t.type === 'upload' ? '上传' : '下载'">
                <svg v-if="t.type === 'upload'" viewBox="0 0 12 12" fill="none" class="type-badge-svg">
                  <path d="M6 2v6M6 2L4 4M6 2l2 2M3 9h6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                <svg v-else viewBox="0 0 12 12" fill="none" class="type-badge-svg">
                  <path d="M6 10V4M6 10L4 8M6 10l2-2M3 3h6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
            </div>

            <div class="task-info">
              <div class="task-name" :title="t.name">{{ t.name }}</div>
              <div class="progress-row">
                <div class="progress-track">
                  <div
                    class="progress-fill"
                    :class="{ error: t.status === 'error', paused: t.status === 'paused' }"
                    :style="{
                      width: `${Math.round(t.progress * 100)}%`,
                      background: progressGradient(t.status)
                    }"
                  />
                </div>
                <span class="progress-pct" :class="{ error: t.status === 'error' }">
                  {{ formatPercent(t.progress) }}
                </span>
              </div>
              <div class="task-meta">
                <span class="meta-size">{{ fmtSize(t.loaded) }} / {{ fmtSize(t.size) }}</span>
                <span class="meta-speed" :class="{ error: t.status === 'error' }">{{ t.speed }}</span>
              </div>
            </div>

            <div class="task-actions">
              <template v-if="t.type === 'upload'">
                <button
                  v-if="t.status === 'running'"
                  class="action-circle pause"
                  title="暂停"
                  @click="transferStore.pauseTask(t.id)"
                >
                  ⏸
                </button>
                <button
                  v-else-if="t.status === 'paused'"
                  class="action-circle resume"
                  title="继续"
                  @click="transferStore.resumeTask(t.id)"
                >
                  ▶
                </button>
              </template>
              <button class="action-circle cancel" title="取消" @click="handleCancelTask(t)">
                ✕
              </button>
            </div>
          </div>
        </div>

        <!-- 已完成 -->
        <div v-else class="task-list">
          <div v-if="!completedTasks.length" class="empty-state">
            <p>暂无完成记录</p>
            <span>传输完成的任务将在此显示</span>
          </div>

          <div
            v-for="t in completedTasks"
            :key="t.id"
            class="task-card task-card--done"
            :class="{ error: t.status === 'error' }"
          >
            <div class="task-icon-area">
              <div
                class="task-icon-box small"
                :class="{ 'has-cover': t.status !== 'error' && taskHasCover(t) }"
                :style="{
                  background: t.status === 'error' ? 'rgba(239,68,68,0.08)' : getFileIconInfo(t.name).bg
                }"
              >
                <img
                  v-if="t.status !== 'error' && taskCoverKind(t) === 'image'"
                  :src="coverSrc(t)"
                  class="task-cover-media"
                  alt=""
                  @error="onCoverError(t)"
                />
                <video
                  v-else-if="t.status !== 'error' && taskCoverKind(t) === 'video'"
                  :src="coverSrc(t)"
                  class="task-cover-media"
                  muted
                  preload="metadata"
                />
                <span v-else class="task-emoji">{{ t.status === 'error' ? '❌' : getFileIconInfo(t.name).icon }}</span>
              </div>
            </div>

            <div class="task-info task-info--done">
              <div class="done-title-row">
                <div class="task-name done-name" :title="t.name">{{ t.name }}</div>
              </div>
              <div class="done-sub-row">
                <span class="meta-size">{{ fmtSize(t.size) }}</span>
                <span class="meta-divider">·</span>
                <span class="meta-type">{{ t.type === 'upload' ? '上传' : '下载' }}</span>
                <span
                  class="status-chip"
                  :class="{
                    success: t.status === 'done',
                    instant: t.status === 'instant',
                    fail: t.status === 'error'
                  }"
                >
                  <template v-if="t.status === 'instant'">秒传</template>
                  <template v-else-if="t.status === 'done'">已完成</template>
                  <template v-else>失败</template>
                </span>
              </div>
            </div>

            <button class="done-remove-btn" title="删除记录" @click="handleCancelTask(t)">
              <svg viewBox="0 0 24 24" fill="none" class="remove-svg">
                <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cd-transfer-container {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 2000;
}

/* 折叠胶囊 */
.cd-transfer-mini {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px 10px 10px;
  border-radius: 99px;
  background: var(--cd-primary-gradient);
  color: #fff;
  cursor: pointer;
  box-shadow: 0 8px 24px color-mix(in srgb, var(--cd-primary) 28%, transparent);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.cd-transfer-mini:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px color-mix(in srgb, var(--cd-primary) 32%, transparent);
}

.mini-inner {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.mini-inner.pulse {
  animation: miniPulse 1.8s ease-in-out infinite;
}

@keyframes miniPulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.25);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(255, 255, 255, 0);
  }
}

.mini-icon {
  width: 18px;
  height: 18px;
  color: #fff;
}

.mini-text {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.mini-badge {
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 99px;
  background: var(--cd-primary);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 主面板 */
.cd-transfer-panel {
  width: 400px;
  max-height: 520px;
  display: flex;
  flex-direction: column;
  border-radius: 20px;
  background: var(--cd-bg, #f5f6fa);
  border: 1px solid var(--cd-border-light, #e2e8f0);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.12);
  overflow: hidden;
  animation: panelSlideUp 0.3s ease;
}

@keyframes panelSlideUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 16px 18px 12px;
  flex-shrink: 0;
}

.header-left {
  min-width: 0;
}

.header-title {
  display: block;
  font-size: 16px;
  font-weight: 800;
  color: var(--cd-text-primary, #1e293b);
}

.header-sub {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--cd-text-secondary, #64748b);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.header-text-btn {
  padding: 6px 12px;
  border: 1px solid var(--cd-border-light, #e2e8f0);
  border-radius: 99px;
  background: #fff;
  color: var(--cd-text-primary, #1e293b);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s ease;
}

.header-text-btn:hover {
  background: #f1f5f9;
}

.header-icon-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 10px;
  background: #fff;
  color: #94a3b8;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cd-border-light, #e2e8f0);
}

.header-icon-btn:hover {
  color: #475569;
  background: #f8fafc;
}

.action-svg {
  width: 16px;
  height: 16px;
}

/* Tab */
.panel-tabs {
  display: flex;
  gap: 6px;
  padding: 0 14px 12px;
  flex-shrink: 0;
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 0;
  border: none;
  border-radius: 12px;
  background: #fff;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  transition: all 0.22s ease;
}

.tab-btn.active {
  background: var(--cd-primary-gradient);
  color: #fff;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--cd-primary) 18%, transparent);
}

.tab-count {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 99px;
  background: rgba(148, 163, 184, 0.15);
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.tab-btn.active .tab-count {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

/* 列表 */
.panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 14px 14px;
}

.panel-body::-webkit-scrollbar {
  width: 4px;
}

.panel-body::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 2px;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 36px 16px;
  gap: 6px;
  text-align: center;
}

.empty-state p {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
}

.empty-state span {
  font-size: 12px;
  color: #94a3b8;
}

/* 任务卡片 */
.task-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.04);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}

.task-card.error {
  border-color: rgba(239, 68, 68, 0.12);
  background: rgba(254, 242, 242, 0.5);
}

.task-card.paused {
  border-color: rgba(245, 158, 11, 0.12);
  background: rgba(255, 251, 235, 0.6);
}

.task-card--done {
  align-items: center;
  padding: 12px;
  gap: 10px;
}

.task-card--done.error {
  border-color: rgba(239, 68, 68, 0.15);
  background: rgba(254, 242, 242, 0.45);
}

/* 封面区 */
.task-icon-area {
  position: relative;
  flex-shrink: 0;
}

.task-icon-box {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.task-icon-box.small {
  width: 44px;
  height: 44px;
  border-radius: 12px;
}

.task-icon-box.has-cover {
  background: transparent !important;
  border: 1px solid var(--cd-border-light, #e2e8f0);
}

.task-cover-media {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.task-emoji {
  font-size: 24px;
  line-height: 1;
}

.task-type-badge {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 14px;
  height: 14px;
  border-radius: 4px;
  border: 1.5px solid #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.18);
}

.type-badge-svg {
  width: 10px;
  height: 10px;
  display: block;
}

.task-type-badge.upload {
  background: color-mix(in srgb, var(--cd-primary) 92%, #000);
}

.task-type-badge.download {
  background: rgba(217, 119, 6, 0.92);
}

/* 信息区 */
.task-info {
  flex: 1;
  min-width: 0;
}

.task-info--done {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.done-title-row {
  min-width: 0;
}

.done-name {
  min-width: 0;
  margin-bottom: 0;
}

.done-sub-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 20px;
  font-size: 11px;
  color: #94a3b8;
  line-height: 1;
}

.done-sub-row .status-chip {
  margin-left: 2px;
}

.meta-divider {
  opacity: 0.6;
}

.meta-type {
  font-weight: 500;
}

.task-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--cd-text-primary, #1e293b);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 8px;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.progress-track {
  flex: 1;
  height: 6px;
  border-radius: 99px;
  background: #f1f5f9;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 99px;
  transition: width 0.3s ease;
}

.progress-fill:not(.error):not(.paused)::after {
  content: '';
  display: block;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.35), transparent);
  animation: shimmer 2s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

.progress-pct {
  font-size: 11px;
  font-weight: 700;
  color: var(--cd-text-primary, #1e293b);
  width: 36px;
  text-align: right;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.progress-pct.error {
  color: #ef4444;
}

.task-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.status-chip {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 600;
  padding: 3px 7px;
  border-radius: 999px;
  white-space: nowrap;
  line-height: 1;
  vertical-align: middle;
}

.meta-size {
  font-size: 11px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.meta-speed {
  font-size: 11px;
  font-weight: 700;
  color: var(--cd-text-primary, #1e293b);
  font-variant-numeric: tabular-nums;
}

.meta-speed.error {
  color: #ef4444;
}

.status-chip.success {
  background: rgba(34, 197, 94, 0.1);
  color: #16a34a;
}

.status-chip.instant {
  background: color-mix(in srgb, var(--cd-primary) 10%, transparent);
  color: var(--cd-primary);
}

.status-chip.fail {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}

/* 操作 */
.task-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
}

.action-circle {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  line-height: 1;
  color: #94a3b8;
  transition: all 0.15s ease;
  padding: 0;
}

.action-circle:hover {
  transform: scale(1.05);
}

.action-circle.pause {
  background: rgba(245, 158, 11, 0.08);
  border-color: rgba(245, 158, 11, 0.2);
  color: #d97706;
}

.action-circle.resume {
  background: color-mix(in srgb, var(--cd-primary) 8%, #ffffff);
  border-color: color-mix(in srgb, var(--cd-primary) 20%, transparent);
  color: var(--cd-primary);
}

.action-circle.cancel,
.action-circle.remove {
  border-color: transparent;
  background: transparent;
}

.action-circle.cancel:hover,
.action-circle.remove:hover {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
}

.done-remove-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #b0bec9;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease;
  padding: 0;
}

.done-remove-btn:hover {
  background: rgba(148, 163, 184, 0.12);
  color: #64748b;
}

.remove-svg {
  width: 14px;
  height: 14px;
}
</style>
