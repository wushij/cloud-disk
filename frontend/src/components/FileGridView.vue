<script setup lang="ts">

import { computed } from 'vue'

import { fmtSize, fileIconColor, transcodeLabel } from '@/utils/fileMeta'
import { fileCoverKind, fileCoverUrl, fileIsVideoCover } from '@/utils/fileCover'
import CachedCover from '@/components/CachedCover.vue'
import type { FileItem } from '@/stores/file'
import FolderTypeIcon from './FolderTypeIcon.vue'
import { sanitizeHighlight } from '@/utils/sanitize'

export type { FileItem }

const props = defineProps<{
  items: FileItem[]
  loading?: boolean
  /** 精简操作：仅保留打开/下载/预览/删除 */
  simple?: boolean
  selectedIds?: number[]
}>()

const emit = defineEmits<{
  (e: 'open', row: FileItem): void
  (e: 'download', row: FileItem): void
  (e: 'directDownload', row: FileItem): void
  (e: 'preview', row: FileItem): void
  (e: 'share', row: FileItem): void
  (e: 'move', row: FileItem): void
  (e: 'copy', row: FileItem): void
  (e: 'rename', row: FileItem): void
  (e: 'delete', row: FileItem): void
  (e: 'update:selectedIds', val: number[]): void
}>()

const isSelecting = computed(() => !!props.selectedIds && props.selectedIds.length > 0)

function isSelected(row: FileItem) {
  return props.selectedIds?.includes(row.id) ?? false
}

function toggleSelect(row: FileItem, checked: boolean) {
  const current = props.selectedIds ? [...props.selectedIds] : []
  if (checked) {
    if (!current.includes(row.id)) {
      current.push(row.id)
    }
  } else {
    const idx = current.indexOf(row.id)
    if (idx >= 0) {
      current.splice(idx, 1)
    }
  }
  emit('update:selectedIds', current)
}

function onCardClick(row: FileItem) {
  if (isSelecting.value) {
    toggleSelect(row, !isSelected(row))
  } else {
    emit('open', row)
  }
}





function fileExt(row: FileItem): string {

  if (row.type === 'folder') return ''

  const name = row.name || ''

  const dot = name.lastIndexOf('.')

  return dot > 0 ? name.substring(dot + 1).toUpperCase() : ''

}

const isArchive = (row: FileItem) => {
  if (row.type !== 'file') return false
  const ext = fileExt(row).toLowerCase()
  return ['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)
}





const gridItems = computed(() => props.items)

function onMoreCommand(command: string, row: FileItem) {
  switch (command) {
    case 'move':
      emit('move', row)
      break
    case 'copy':
      emit('copy', row)
      break
    case 'rename':
      emit('rename', row)
      break
    case 'delete':
      emit('delete', row)
      break
  }
}

function formatDate(value?: string) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`
}

</script>



<template>

  <div v-loading="loading" class="cd-grid-view">

    <div v-if="!gridItems.length" class="cd-grid-empty">
      <slot name="empty">
        <el-icon :size="48" color="var(--cd-text-placeholder)"><FolderOpened /></el-icon>
        <p>暂无文件</p>
      </slot>
    </div>

    <div v-else class="cd-grid-container" :class="{ 'is-selecting': isSelecting }">

      <div

        v-for="row in gridItems"

        :key="row.id"

        class="cd-grid-card"
        :class="{ 'cd-grid-card-folder': row.type === 'folder', 'is-selected': isSelected(row) }"

        @click="onCardClick(row)"
        @dblclick="emit('open', row)"

      >
        <!-- 多选勾选框 -->
        <div class="cd-grid-checkbox-wrap" @click.stop>
          <el-checkbox
            :model-value="isSelected(row)"
            @change="toggleSelect(row, !isSelected(row))"
          />
        </div>

        <!-- 缩略图/图标区域 -->

        <div class="cd-grid-thumb">

          <div v-if="fileCoverKind(row) === 'image'" class="cd-grid-thumb-cover">
            <CachedCover
              :file-id="row.id"
              :src="fileCoverUrl(row)"
              :has-thumbnail="row.hasThumbnail"
              img-class="cd-grid-thumb-img"
            />
            <span v-if="fileIsVideoCover(row)" class="cd-grid-play-badge">▶</span>
          </div>
          <div v-else class="cd-grid-icon" :style="{ color: fileIconColor(row) }">
            <FolderTypeIcon v-if="row.type === 'folder'" :size="48" />
            <FolderTypeIcon v-else-if="isArchive(row)" :archive="true" :size="48" />
            <el-icon v-else :size="32">
              <Document v-if="row.mimeType?.includes('pdf')" />
              <Picture v-else-if="row.mimeType?.startsWith('image/')" />
              <VideoPlay v-else-if="row.mimeType?.startsWith('video/')" />
              <Document v-else />
            </el-icon>
            <span v-if="fileExt(row)" class="cd-grid-ext">{{ fileExt(row) }}</span>
          </div>

          <!-- 转码标签 -->

          <span

            v-if="row.type === 'file' && transcodeLabel(row.transcodeStatus)"

            class="cd-grid-badge"

            :class="{

              'cd-grid-badge-info': !row.transcodeStatus || row.transcodeStatus === 'PENDING' || row.transcodeStatus === 'PROCESSING',

              'cd-grid-badge-success': row.transcodeStatus === 'DONE',

              'cd-grid-badge-danger': row.transcodeStatus === 'FAILED'

            }"

          >{{ transcodeLabel(row.transcodeStatus) }}</span>

        </div>



        <!-- 信息区域 -->

        <div class="cd-grid-info">

          <div class="cd-grid-name" :title="row.name">

            <span v-if="row.highlightName" v-html="sanitizeHighlight(row.highlightName)" />
            <span v-else>{{ row.name }}</span>

          </div>

          <div class="cd-grid-meta">
            <span class="cd-grid-meta-left">
              <template v-if="row.type === 'file'">{{ fmtSize(row.sizeBytes || 0) }}</template>
              <template v-else>文件夹</template>
            </span>
            <span v-if="row.createdAt" class="cd-grid-meta-date">{{ formatDate(row.createdAt) }}</span>
          </div>

        </div>



        <!-- 悬浮操作栏 -->

        <div class="cd-grid-actions">

          <button

            v-if="row.type === 'folder'"

            class="cd-grid-action-btn"

            title="打开"

            @click.stop="emit('open', row)"

          >

            <el-icon><FolderOpened /></el-icon>

          </button>

          <button

            v-if="row.type === 'folder'"

            class="cd-grid-action-btn"

            title="打包下载"

            @click.stop="emit('download', row)"

          >

            <el-icon><Download /></el-icon>

          </button>

          <button

            v-if="row.type === 'file'"

            class="cd-grid-action-btn"

            title="下载"

            @click.stop="emit('download', row)"

          >

            <el-icon><Download /></el-icon>

          </button>

          <button

            v-if="row.previewable"

            class="cd-grid-action-btn"

            title="预览"

            @click.stop="emit('preview', row)"

          >

            <el-icon><View /></el-icon>

          </button>

          <button

            v-if="!simple"

            class="cd-grid-action-btn"

            title="分享"

            @click.stop="emit('share', row)"

          >

            <el-icon><Share /></el-icon>

          </button>

          <el-dropdown
            v-if="!simple"
            trigger="click"
            placement="bottom-end"
            :teleported="true"
            @command="(cmd: string) => onMoreCommand(cmd, row)"
          >
            <button
              type="button"
              class="cd-grid-action-btn cd-grid-action-more"
              aria-label="更多操作"
              @click.stop
            >
              <el-icon><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="move">
                  <el-icon><Rank /></el-icon>
                  移动
                </el-dropdown-item>
                <el-dropdown-item v-if="row.type === 'file'" command="copy">
                  <el-icon><CopyDocument /></el-icon>
                  复制
                </el-dropdown-item>
                <el-dropdown-item command="rename">
                  <el-icon><Edit /></el-icon>
                  重命名
                </el-dropdown-item>
                <el-dropdown-item command="delete" divided>
                  <span class="cd-grid-menu-danger">
                    <el-icon><Delete /></el-icon>
                    删除
                  </span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <button
            v-if="simple && row.canDelete !== false"
            type="button"
            class="cd-grid-action-btn cd-grid-action-more"
            aria-label="删除"
            title="删除"
            @click.stop="emit('delete', row)"
          >
            <el-icon><Delete /></el-icon>
          </button>

        </div>

      </div>

    </div>

  </div>

</template>



<style scoped>

/* ============================================

   网格视图容器

   ============================================ */

.cd-grid-view {

  min-height: 200px;

}



.cd-grid-container {

  display: grid;

  grid-template-columns: repeat(auto-fill, minmax(140px, 168px));

  gap: 14px;

  padding: 2px 0;

}



.cd-grid-card-folder {

  border-color: color-mix(in srgb, var(--cd-file-folder) 22%, var(--cd-border-light));

}



.cd-grid-card-folder .cd-grid-thumb {

  background: color-mix(in srgb, var(--cd-file-folder) 10%, #f8f9fc);

}



.cd-grid-card-folder:hover {

  border-color: color-mix(in srgb, var(--cd-file-folder) 45%, var(--cd-primary-light));

}



/* ============================================

   空状态

   ============================================ */

.cd-grid-empty {

  display: flex;

  flex-direction: column;

  align-items: center;

  justify-content: center;

  padding: 60px 20px;

  color: var(--cd-text-placeholder);

  gap: 12px;

}



.cd-grid-empty p {

  font-size: 14px;

  margin: 0;

}



/* ============================================

   文件卡片

   ============================================ */

.cd-grid-card {

  background: #fff;

  border: 1px solid var(--cd-border-light);

  border-radius: 12px;

  overflow: hidden;

  cursor: pointer;

  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;

  position: relative;

  display: flex;

  flex-direction: column;

  isolation: isolate;

}



.cd-grid-card:hover {

  border-color: color-mix(in srgb, var(--cd-primary) 25%, var(--cd-border-light));

  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);

  transform: translateY(-2px);

}



/* ============================================

   缩略图/图标

   ============================================ */

.cd-grid-thumb {

  width: 100%;

  height: 96px;

  background: linear-gradient(135deg, #F8F9FC 0%, color-mix(in srgb, var(--theme-primary) 4%, #F8F9FC) 100%);

  display: flex;

  align-items: center;

  justify-content: center;

  position: relative;

  overflow: hidden;

}



.cd-grid-thumb-img {

  width: 100%;

  height: 100%;

  object-fit: cover;

  transition: var(--cd-transition-slow);

}



.cd-grid-thumb-video {

  pointer-events: none;

  background: #000;

}

.cd-grid-thumb-cover {
  position: relative;
  width: 100%;
  height: 100%;
}

.cd-grid-play-badge {
  position: absolute;
  right: 6px;
  bottom: 6px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}



.cd-grid-card:hover .cd-grid-thumb-img {

  transform: scale(1.05);

}



.cd-grid-icon {

  display: flex;

  flex-direction: column;

  align-items: center;

  gap: 6px;

  position: relative;

}



.cd-grid-ext {

  font-size: 10px;

  font-weight: 700;

  color: var(--cd-text-secondary);

  background: rgba(255, 255, 255, 0.85);

  backdrop-filter: blur(4px);

  padding: 2px 8px;

  border-radius: var(--cd-radius-full);

  letter-spacing: 0.5px;

  box-shadow: var(--cd-shadow-sm);

}



.cd-grid-badge {

  position: absolute;

  top: 8px;

  right: 8px;

  font-size: 10px;

  font-weight: 600;

  padding: 2px 8px;

  border-radius: var(--cd-radius-full);

  backdrop-filter: blur(4px);

}

.cd-grid-badge-info { background: rgba(59, 130, 246, 0.9); color: #fff; }

.cd-grid-badge-success { background: rgba(34, 197, 94, 0.9); color: #fff; }

.cd-grid-badge-danger { background: rgba(239, 68, 68, 0.9); color: #fff; }



/* ============================================

   信息区域

   ============================================ */

.cd-grid-info {

  padding: 8px 10px 10px;

  flex: 1;

  min-height: 0;

  background: #fff;

}



.cd-grid-name {

  font-size: 13px;

  font-weight: 600;

  color: var(--cd-text-primary);

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

  line-height: 1.45;

}



.cd-grid-meta {

  display: flex;

  align-items: center;

  justify-content: space-between;

  gap: 10px;

  font-size: 11px;

  color: var(--cd-text-placeholder);

  margin-top: 4px;

  line-height: 1.4;

}

.cd-grid-meta-left {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex-shrink: 1;
}

.cd-grid-meta-date {
  flex-shrink: 0;
  color: var(--cd-text-secondary);
  font-variant-numeric: tabular-nums;
}



/* ============================================

   悬浮操作栏

   ============================================ */

.cd-grid-actions {

  position: absolute;

  top: 0;

  left: 0;

  right: 0;

  aspect-ratio: unset;

  height: 85px;

  display: flex;

  align-items: center;

  justify-content: center;

  gap: 6px;

  background: linear-gradient(180deg, rgba(15, 23, 42, 0.42) 0%, rgba(15, 23, 42, 0.08) 80%, transparent 100%);

  backdrop-filter: blur(6px);

  -webkit-backdrop-filter: blur(6px);

  opacity: 0;

  transition: opacity 0.2s ease;

  padding: 10px;

  flex-wrap: wrap;

  z-index: 2;

}



.cd-grid-card:hover .cd-grid-actions {

  opacity: 1;

}



.cd-grid-action-btn {

  width: 30px;

  height: 30px;

  border-radius: 8px;

  border: none;

  background: rgba(255, 255, 255, 0.94);

  color: var(--cd-text-primary);

  display: flex;

  align-items: center;

  justify-content: center;

  cursor: pointer;

  transition: var(--cd-transition-bounce);

  font-size: 16px;

  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);

}



.cd-grid-action-btn:hover {

  background: #fff;

  color: var(--cd-primary);

  transform: scale(1.08);

  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.16);

}



.cd-grid-action-more {

  position: relative;

}

.cd-grid-menu-danger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--cd-danger);
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cd-grid-card.is-selected {
  border-color: var(--cd-primary) !important;
  background: var(--cd-primary-light-9, rgba(99, 102, 241, 0.05)) !important;
  box-shadow: 0 0 0 1px var(--cd-primary), var(--cd-shadow-card) !important;
}

.cd-grid-checkbox-wrap {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 3;
  opacity: 0;
  transition: opacity 0.2s ease;
  pointer-events: auto;
}

.cd-grid-card:hover .cd-grid-checkbox-wrap,
.cd-grid-card.is-selected .cd-grid-checkbox-wrap,
.cd-grid-container.is-selecting .cd-grid-checkbox-wrap {
  opacity: 1;
}

/* 升级为高质感圆形磨砂多选框 */
.cd-grid-checkbox-wrap :deep(.el-checkbox__inner) {
  width: 20px;
  height: 20px;
  border-radius: 50% !important;
  background: rgba(255, 255, 255, 0.72) !important;
  backdrop-filter: blur(6px) !important;
  -webkit-backdrop-filter: blur(6px) !important;
  border: 1.5px solid rgba(255, 255, 255, 0.9) !important;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.25) !important;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1) !important;
}

.cd-grid-checkbox-wrap:hover :deep(.el-checkbox__inner) {
  background: rgba(255, 255, 255, 0.9) !important;
  border-color: #ffffff !important;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.18) !important;
  transform: scale(1.05);
}

.cd-grid-checkbox-wrap :deep(.el-checkbox.is-checked .el-checkbox__inner) {
  background: var(--cd-primary, #6366f1) !important;
  border-color: var(--cd-primary, #6366f1) !important;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.38) !important;
}

/* 调整对勾形状与绝对居中 */
.cd-grid-checkbox-wrap :deep(.el-checkbox__inner::after) {
  left: 50% !important;
  top: 50% !important;
  width: 5px !important;
  height: 9px !important;
  border-width: 2px !important;
  border-color: #ffffff !important;
  transform: translate(-50%, -52%) rotate(45deg) scaleY(0) !important;
  transform-origin: center !important;
  transition: transform 0.15s ease-in-out !important;
}

.cd-grid-checkbox-wrap :deep(.el-checkbox.is-checked .el-checkbox__inner::after),
.cd-grid-checkbox-wrap :deep(.el-checkbox__input.is-checked .el-checkbox__inner::after) {
  transform: translate(-50%, -52%) rotate(45deg) scaleY(1) !important;
}
</style>
