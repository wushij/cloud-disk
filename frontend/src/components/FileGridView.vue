<script setup lang="ts">

import { computed } from 'vue'

import { fmtSize, fileIconColor, transcodeLabel } from '@/utils/fileMeta'
import { fileCoverKind, fileCoverUrl } from '@/utils/fileCover'
import type { FileItem } from '@/stores/file'

export type { FileItem }

const props = defineProps<{

  items: FileItem[]

  loading?: boolean

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

}>()





function fileExt(row: FileItem): string {

  if (row.type === 'folder') return ''

  const name = row.name || ''

  const dot = name.lastIndexOf('.')

  return dot > 0 ? name.substring(dot + 1).toUpperCase() : ''

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

</script>



<template>

  <div v-loading="loading" class="cd-grid-view">

    <div v-if="!gridItems.length" class="cd-grid-empty">
      <slot name="empty">
        <el-icon :size="48" color="var(--cd-text-placeholder)"><FolderOpened /></el-icon>
        <p>暂无文件</p>
      </slot>
    </div>

    <div v-else class="cd-grid-container">

      <div

        v-for="row in gridItems"

        :key="row.id"

        class="cd-grid-card"
        :class="{ 'cd-grid-card-folder': row.type === 'folder' }"

        @dblclick="emit('open', row)"

      >

        <!-- 缩略图/图标区域 -->

        <div class="cd-grid-thumb">

          <img
            v-if="fileCoverKind(row) === 'image'"
            :src="fileCoverUrl(row)"
            class="cd-grid-thumb-img"
            alt=""
            loading="lazy"
          />
          <video
            v-else-if="fileCoverKind(row) === 'video'"
            :src="fileCoverUrl(row)"
            class="cd-grid-thumb-img cd-grid-thumb-video"
            muted
            preload="metadata"
            playsinline
          />
          <div v-else class="cd-grid-icon" :style="{ color: fileIconColor(row) }">

            <el-icon :size="40">

              <Folder v-if="row.type === 'folder'" />

              <Document v-else-if="row.mimeType?.includes('pdf')" />

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

            <span v-if="row.highlightName" v-html="row.highlightName" />

            <span v-else>{{ row.name }}</span>

          </div>

          <div class="cd-grid-meta">

            <span v-if="row.type === 'file'">{{ fmtSize(row.sizeBytes || 0) }}</span>

            <span v-else>文件夹</span>

            <span v-if="row.createdAt">{{ new Date(row.createdAt).toLocaleDateString() }}</span>

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

            class="cd-grid-action-btn"

            title="分享"

            @click.stop="emit('share', row)"

          >

            <el-icon><Share /></el-icon>

          </button>

          <el-dropdown
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

  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));

  gap: 18px;

  padding: 4px 0;

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

  background: var(--cd-bg-white);

  border: 1px solid var(--cd-border-light);

  border-radius: 14px;

  overflow: visible;

  cursor: pointer;

  transition: var(--cd-transition);

  position: relative;

  display: flex;

  flex-direction: column;

  isolation: isolate;

}



.cd-grid-card::before {

  content: '';

  position: absolute;

  inset: 0;

  border-radius: 14px;

  padding: 1px;

  background: linear-gradient(135deg, var(--cd-primary-light) 0%, transparent 50%);

  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);

  -webkit-mask-composite: xor;

  mask-composite: exclude;

  opacity: 0;

  transition: opacity 0.25s ease;

  pointer-events: none;

  z-index: 3;

}



.cd-grid-card:hover {

  border-color: transparent;

  box-shadow: var(--cd-shadow-lg), 0 0 0 1px var(--theme-primary-muted);

  transform: translateY(-3px);

}



.cd-grid-card:hover::before {

  opacity: 1;

}



/* ============================================

   缩略图/图标

   ============================================ */

.cd-grid-thumb {

  width: 100%;

  height: 152px;

  background: linear-gradient(135deg, #F8F9FC 0%, color-mix(in srgb, var(--theme-primary) 4%, #F8F9FC) 100%);

  display: flex;

  align-items: center;

  justify-content: center;

  position: relative;

  overflow: hidden;

  border-radius: 14px 14px 0 0;

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

  padding: 11px 13px 14px;

  flex: 1;

  min-height: 0;

}



.cd-grid-name {

  font-size: 13px;

  font-weight: 500;

  color: var(--cd-text-primary);

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

  line-height: 1.4;

}



.cd-grid-meta {

  display: flex;

  justify-content: space-between;

  font-size: 11px;

  color: var(--cd-text-placeholder);

  margin-top: 4px;
  line-height: 1.5;
  gap: 8px;
  min-width: 0;

}

.cd-grid-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex-shrink: 1;
}



/* ============================================

   悬浮操作栏

   ============================================ */

.cd-grid-actions {

  position: absolute;

  top: 0;

  left: 0;

  right: 0;

  height: 145px;

  display: flex;

  align-items: center;

  justify-content: center;

  gap: 5px;

  background: linear-gradient(180deg, rgba(15, 23, 42, 0.5) 0%, rgba(15, 23, 42, 0.1) 85%, transparent 100%);

  backdrop-filter: blur(8px);

  -webkit-backdrop-filter: blur(8px);

  opacity: 0;

  transition: var(--cd-transition);

  padding: 8px;

  flex-wrap: wrap;

  border-radius: 14px 14px 0 0;

  z-index: 2;

}



.cd-grid-card:hover .cd-grid-actions {

  opacity: 1;

}



.cd-grid-action-btn {

  width: 34px;

  height: 34px;

  border-radius: var(--cd-radius);

  border: none;

  background: rgba(255, 255, 255, 0.92);

  color: var(--cd-text-primary);

  display: flex;

  align-items: center;

  justify-content: center;

  cursor: pointer;

  transition: var(--cd-transition-bounce);

  font-size: 16px;

  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);

}



.cd-grid-action-btn:hover {

  background: var(--cd-primary-gradient);

  color: #fff;

  transform: scale(1.15);

  box-shadow: 0 4px 12px var(--theme-primary-muted-strong);

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

</style>
