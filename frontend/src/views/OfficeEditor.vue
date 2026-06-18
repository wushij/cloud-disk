<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'

const route = useRoute()
const loading = ref(true)
const documentServerUrl = ref('')
const config = ref<Record<string, unknown> | null>(null)

onMounted(async () => {
  const id = Number(route.params.id)
  if (!id) {
    ElMessage.error('文件 ID 无效')
    loading.value = false
    return
  }
  try {
    const { data } = await http.get(`/api/files/${id}/onlyoffice`)
    documentServerUrl.value = data.documentServerUrl
    config.value = data.config
  } catch {
    /* global toast */
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" class="office-page">
    <OnlyOfficeEditor
      v-if="documentServerUrl && config"
      :document-server-url="documentServerUrl"
      :config="config"
    />
    <el-empty v-else-if="!loading" description="在线文档预览未启用或文件不可编辑" />
  </div>
</template>

<style scoped>
.office-page {
  min-height: calc(100vh - 120px);
  padding: 8px;
}
</style>
