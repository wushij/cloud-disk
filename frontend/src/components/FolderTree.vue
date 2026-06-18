<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import http from '@/api/http'

export interface TreeNode {
  id: number
  label: string
  parentId?: number
  children?: TreeNode[]
}

const props = defineProps<{ currentId: number }>()
const emit = defineEmits<{ select: [number, string] }>()

const tree = ref<TreeNode[]>([])
const defaultProps = { children: 'children', label: 'label' }

async function loadTree() {
  const { data } = await http.get('/api/folders/tree')
  tree.value = [{ id: 0, label: '全部文件', children: data }]
}

function onNodeClick(node: TreeNode) {
  emit('select', node.id, node.label)
}

onMounted(loadTree)
watch(() => props.currentId, () => {})
defineExpose({ reload: loadTree })
</script>

<template>
  <el-tree
    :data="tree"
    :props="defaultProps"
    node-key="id"
    highlight-current
    :current-node-key="currentId"
    default-expand-all
    @node-click="onNodeClick"
  >
    <template #default="{ node, data }">
      <span class="cd-folder-node">
        <el-icon :size="14" color="var(--cd-file-folder)">
          <FolderOpened v-if="data.id === 0" />
          <Folder v-else />
        </el-icon>
        <span class="cd-folder-label">{{ node.label }}</span>
      </span>
    </template>
  </el-tree>
</template>

<style scoped>
.cd-folder-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px;
  border-radius: var(--cd-radius-sm);
  transition: var(--cd-transition-fast);
}

.cd-folder-label {
  font-size: 13px;
  color: var(--cd-text-primary);
}

:deep(.el-tree-node__content) {
  height: 30px;
  border-radius: var(--cd-radius-sm);
  transition: var(--cd-transition-fast);
  padding-left: 6px !important;
}

:deep(.el-tree-node__content:hover) {
  background: rgba(79, 124, 255, 0.06);
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--cd-primary-bg);
}

:deep(.el-tree-node.is-current > .el-tree-node__content .cd-folder-label) {
  color: var(--cd-primary);
  font-weight: 600;
}
</style>
