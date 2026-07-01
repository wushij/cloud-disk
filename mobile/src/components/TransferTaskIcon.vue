<script setup lang="ts">
import FolderTypeIcon from '@/components/FolderTypeIcon.vue'
import {
  fileExtLabelFromName,
  fileTypeColorFromName,
  fileTypeIconFromName,
  fileTypeKindFromName
} from '@/utils/fileType'

withDefaults(
  defineProps<{
    name: string
    size?: number
    error?: boolean
  }>(),
  { size: 44, error: false }
)
</script>

<template>
  <view v-if="error" class="transfer-task-icon error">
    <text class="error-mark">❌</text>
  </view>
  <view v-else class="transfer-task-icon" :class="fileTypeKindFromName(name)">
    <FolderTypeIcon
      v-if="fileTypeKindFromName(name) === 'archive'"
      archive
      :size="size"
    />
    <template v-else>
      <u-icon
        :name="fileTypeIconFromName(name)"
        :size="Math.round(size * 0.72)"
        :color="fileTypeColorFromName(name)"
      />
      <text class="transfer-task-ext">{{ fileExtLabelFromName(name) }}</text>
    </template>
  </view>
</template>

<style scoped lang="scss">
.transfer-task-icon {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3rpx;
  background: linear-gradient(145deg, rgba(1, 7, 16, 0.04), rgba(15, 26, 46, 0.02));
}

.transfer-task-icon.archive,
.transfer-task-icon.pdf,
.transfer-task-icon.doc,
.transfer-task-icon.sheet,
.transfer-task-icon.slide,
.transfer-task-icon.audio,
.transfer-task-icon.code {
  background: transparent;
}

.transfer-task-icon.archive .transfer-task-ext {
  color: #c2410c;
}

.transfer-task-icon.pdf .transfer-task-ext {
  color: #dc2626;
}

.transfer-task-icon.doc .transfer-task-ext {
  color: #2563eb;
}

.transfer-task-icon.sheet .transfer-task-ext {
  color: #059669;
}

.transfer-task-icon.slide .transfer-task-ext {
  color: #d97706;
}

.transfer-task-icon.audio .transfer-task-ext {
  color: #db2777;
}

.transfer-task-icon.code .transfer-task-ext {
  color: #4f46e5;
}

.transfer-task-ext {
  font-size: 16rpx;
  font-weight: 700;
  color: var(--cd-text-muted);
  letter-spacing: 0.5rpx;
}

.transfer-task-icon.error {
  background: rgba(239, 68, 68, 0.08);
}

.error-mark {
  font-size: 36rpx;
  line-height: 1;
}
</style>
