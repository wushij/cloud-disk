<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const props = defineProps<{
  modelValue: boolean
  mode: 'move' | 'copy'
  itemType: 'file' | 'folder'
  itemId: number | null
  itemName: string
}>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; done: [] }>()

interface TreeNode {
  id: number
  label: string
  children?: TreeNode[]
}

const tree = ref<TreeNode[]>([])
const targetFolderId = ref(0)

watch(
  () => props.modelValue,
  async (v) => {
    if (v) {
      targetFolderId.value = 0
      const { data } = await http.get('/api/folders/tree')
      tree.value = [{ id: 0, label: '全部文件', children: data }]
    }
  }
)

function close() {
  emit('update:modelValue', false)
}

async function confirm() {
  if (!props.itemId) return
  if (props.itemType === 'folder' && props.mode === 'copy') {
    ElMessage.warning('文件夹暂不支持复制')
    return
  }
  try {
    const body = { targetFolderId: targetFolderId.value }
    const base = props.itemType === 'folder' ? '/api/folders' : '/api/files'
    if (props.mode === 'move') {
      await http.put(`${base}/${props.itemId}/move`, body)
    } else {
      await http.post(`${base}/${props.itemId}/copy`, body)
    }
    ElMessage.success(props.mode === 'move' ? '移动成功' : '复制成功')
    emit('done')
    close()
  } catch (e: unknown) {
    ElMessage.error((e as { response?: { data?: { error?: string } } })?.response?.data?.error || '操作失败')
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    width="440px"
    @close="close"
  >
    <template #header>
      <div class="cd-dialog-header">
        <div class="cd-dialog-icon">
          <el-icon :size="20">
            <Rank v-if="mode === 'move'" />
            <CopyDocument v-else />
          </el-icon>
        </div>
        <div>
          <div class="cd-dialog-title">{{ mode === 'move' ? '移动到' : '复制到' }}</div>
          <div class="cd-dialog-subtitle">{{ itemName }}</div>
        </div>
      </div>
    </template>

    <div class="cd-move-hint">
      <el-icon :size="14"><InfoFilled /></el-icon>
      <span>选择目标文件夹：</span>
    </div>

    <div class="cd-tree-wrapper">
      <el-tree
        :data="tree"
        node-key="id"
        highlight-current
        default-expand-all
        :props="{ children: 'children', label: 'label' }"
        @node-click="(n: TreeNode) => (targetFolderId = n.id)"
      >
        <template #default="{ node, data }">
          <span class="cd-tree-node" :class="{ active: data.id === targetFolderId }">
            <el-icon :size="14" color="var(--cd-file-folder)">
              <FolderOpened v-if="data.id === 0" />
              <Folder v-else />
            </el-icon>
            {{ node.label }}
          </span>
        </template>
      </el-tree>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" @click="confirm">
        <el-icon><Check /></el-icon>
        确定
      </el-button>
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
  width: 40px;
  height: 40px;
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
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-top: 2px;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cd-move-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--cd-text-secondary);
  font-size: 13px;
  margin-bottom: 12px;
}

.cd-tree-wrapper {
  max-height: 360px;
  overflow-y: auto;
  border: 1px solid var(--cd-border);
  border-radius: var(--cd-radius);
  padding: 12px;
  background: var(--cd-bg);
}

.cd-tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: var(--cd-radius-sm);
  font-size: 14px;
  transition: var(--cd-transition-fast);
}

.cd-tree-node.active {
  background: var(--cd-primary-bg);
  color: var(--cd-primary);
  font-weight: 600;
}

.cd-tree-wrapper :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: var(--cd-radius-sm);
  transition: var(--cd-transition-fast);
}

.cd-tree-wrapper :deep(.el-tree-node__content:hover) {
  background: rgba(79, 124, 255, 0.05);
}
</style>
