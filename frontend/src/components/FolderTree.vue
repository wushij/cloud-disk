<script setup lang="ts">
import { ref, onMounted } from 'vue'
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
const TREE_INDENT = 14

async function loadTree() {
  const { data } = await http.get('/api/folders/tree')
  tree.value = [{ id: 0, label: '全部文件', children: data }]
}

function onNodeClick(node: TreeNode) {
  emit('select', node.id, node.label)
}

onMounted(loadTree)
defineExpose({ reload: loadTree })
</script>

<template>
  <el-tree
    class="cd-folder-tree"
    :data="tree"
    :props="defaultProps"
    node-key="id"
    :indent="TREE_INDENT"
    highlight-current
    :current-node-key="currentId"
    :default-expanded-keys="[0]"
    expand-on-click-node
    @node-click="onNodeClick"
  >
    <template #default="{ node, data }">
      <span class="cd-folder-node" :class="{ 'is-root': data.id === 0 }">
        <el-icon :size="15" :color="data.id === 0 ? 'var(--cd-primary)' : 'var(--cd-file-folder)'">
          <FolderOpened v-if="data.id === 0" />
          <Folder v-else />
        </el-icon>
        <span class="cd-folder-label" :title="node.label">{{ node.label }}</span>
      </span>
    </template>
  </el-tree>
</template>

<style scoped>
.cd-folder-tree {
  --cd-tree-indent: 14px;
  background: transparent;
}

.cd-folder-node {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
  padding: 2px 0;
}

.cd-folder-node.is-root .cd-folder-label {
  font-weight: 700;
}

.cd-folder-label {
  font-size: 13px;
  color: var(--cd-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 节点行 */
.cd-folder-tree :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: var(--cd-radius-sm);
  transition: background-color var(--cd-transition-fast);
  padding-right: 8px;
}

.cd-folder-tree :deep(.el-tree-node__content:hover) {
  background: rgba(79, 124, 255, 0.07);
}

.cd-folder-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--cd-primary-bg);
  box-shadow: inset 3px 0 0 var(--cd-primary);
}

.cd-folder-tree :deep(.el-tree-node.is-current > .el-tree-node__content .cd-folder-label) {
  color: var(--cd-primary);
  font-weight: 600;
}

/* 展开箭头 */
.cd-folder-tree :deep(.el-tree-node__expand-icon) {
  width: 18px;
  height: 18px;
  margin-right: 2px;
  padding: 0;
  font-size: 12px;
  color: var(--cd-text-placeholder);
  border-radius: 4px;
  transition: transform var(--cd-transition-fast), color var(--cd-transition-fast);
}

.cd-folder-tree :deep(.el-tree-node__expand-icon:hover) {
  color: var(--cd-primary);
  background: rgba(79, 124, 255, 0.08);
}

.cd-folder-tree :deep(.el-tree-node__expand-icon.is-leaf) {
  color: transparent;
  pointer-events: none;
}

/* 子级竖向引导线 */
.cd-folder-tree :deep(.el-tree-node__children) {
  position: relative;
  margin-left: 6px;
  padding-left: 6px;
  border-left: 1px solid color-mix(in srgb, var(--cd-border) 85%, var(--cd-primary) 15%);
}

/* 层级越深，标签略浅，突出结构 */
.cd-folder-tree :deep(.el-tree-node .el-tree-node .cd-folder-label) {
  font-size: 12.5px;
  color: var(--cd-text-secondary);
}

.cd-folder-tree :deep(.el-tree-node .el-tree-node .el-tree-node .cd-folder-label) {
  font-size: 12px;
  color: var(--cd-text-muted, var(--cd-text-secondary));
}

.cd-folder-tree :deep(.el-tree-node.is-current .cd-folder-label) {
  color: var(--cd-primary);
}
</style>
