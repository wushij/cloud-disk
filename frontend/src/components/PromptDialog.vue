<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import { usePromptDialogStore } from '@/stores/promptDialog'

const store = usePromptDialogStore()
const { visible, title, message, placeholder, confirmText, maxlength, icon, value } = storeToRefs(store)

const inputRef = ref<{ focus: () => void } | null>(null)

watch(visible, async (show) => {
  if (!show) return
  await nextTick()
  inputRef.value?.focus()
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    width="440px"
    class="cd-prompt-dialog"
    :close-on-click-modal="false"
    @close="store.cancel()"
  >
    <template #header>
      <div class="cd-dialog-header">
        <div class="cd-dialog-icon">
          <el-icon :size="20">
            <FolderAdd v-if="icon === 'folder'" />
            <Edit v-else />
          </el-icon>
        </div>
        <div>
          <div class="cd-dialog-title">{{ title }}</div>
          <div v-if="message" class="cd-dialog-subtitle">{{ message }}</div>
        </div>
      </div>
    </template>

    <el-input
      ref="inputRef"
      v-model="value"
      :placeholder="placeholder"
      :maxlength="maxlength"
      clearable
      size="large"
      @keyup.enter="store.confirm()"
    />

    <template #footer>
      <div class="cd-dialog-footer-pills">
        <el-button size="large" @click="store.cancel()">取消</el-button>
        <el-button type="primary" size="large" @click="store.confirm()">
          <el-icon v-if="icon === 'folder'"><FolderAdd /></el-icon>
          {{ confirmText }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.cd-dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cd-dialog-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--cd-radius);
  background: var(--cd-primary-bg);
  color: var(--cd-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cd-dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text-primary);
}

.cd-dialog-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--cd-text-secondary);
  line-height: 1.4;
}

.cd-prompt-dialog :deep(.el-input__wrapper) {
  border-radius: var(--cd-radius);
  box-shadow: 0 0 0 1px var(--cd-border) inset;
  padding: 4px 14px;
}

.cd-prompt-dialog :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--cd-primary) inset, 0 0 0 3px var(--theme-primary-muted, rgba(79, 124, 255, 0.12));
}
</style>
