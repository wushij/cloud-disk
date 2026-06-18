<script setup lang="ts">

import { ref, watch } from 'vue'

import { ElMessage } from 'element-plus'

import http from '@/api/http'



const props = defineProps<{

  modelValue: boolean

  fileId?: number | null

  folderId?: number | null

  itemName?: string

}>()



const emit = defineEmits<{ 'update:modelValue': [boolean] }>()



const extractCode = ref('')

const expireHours = ref<number | null>(24)

const result = ref<{ shareUrl?: string; shareCode?: string; extractCode?: string } | null>(null)

const fullShareUrl = ref('')

const loading = ref(false)

const copied = ref(false)



watch(

  () => props.modelValue,

  (v) => {

    if (v) {

      extractCode.value = ''

      expireHours.value = 24

      result.value = null

      copied.value = false

    }

  }

)



function close() {

  emit('update:modelValue', false)

}



async function createShare() {

  if (!props.fileId && !props.folderId) return

  loading.value = true

  try {

    const body: Record<string, unknown> = {

      extractCode: extractCode.value.trim() || undefined,

      expireHours: expireHours.value || undefined

    }

    if (props.folderId) body.folderId = props.folderId

    else body.fileId = props.fileId

    const { data } = await http.post('/api/share', body)

    result.value = data

    fullShareUrl.value = `${window.location.origin}${data.shareUrl}`

    ElMessage.success('分享创建成功')

  } catch (e: unknown) {

    ElMessage.error((e as { response?: { data?: { error?: string } } })?.response?.data?.error || '分享失败')

  } finally {

    loading.value = false

  }

}



async function copyLink() {

  if (!fullShareUrl.value) return

  await navigator.clipboard.writeText(fullShareUrl.value).catch(() => {})

  copied.value = true

  ElMessage.success('链接已复制')

  setTimeout(() => (copied.value = false), 2000)

}

</script>



<template>

  <el-dialog

    :model-value="modelValue"

    width="480px"

    :show-close="true"

    @close="close"

  >

    <template #header>

      <div class="cd-dialog-header">

        <div class="cd-dialog-icon">

          <el-icon :size="20"><Share /></el-icon>

        </div>

        <div>

          <div class="cd-dialog-title">分享{{ folderId ? '文件夹' : '文件' }}</div>

          <div class="cd-dialog-subtitle">{{ itemName }}</div>

        </div>

      </div>

    </template>



    <!-- 创建表单 -->

    <el-form v-if="!result" label-width="80px">

      <el-form-item label="提取码">

        <el-input v-model="extractCode" placeholder="留空为公开分享" maxlength="16" />

      </el-form-item>

      <el-form-item label="有效期">

        <el-radio-group v-model="expireHours">

          <el-radio-button :value="1">1 小时</el-radio-button>

          <el-radio-button :value="24">24 小时</el-radio-button>

          <el-radio-button :value="168">7 天</el-radio-button>

          <el-radio-button :value="null">永久</el-radio-button>

        </el-radio-group>

      </el-form-item>

    </el-form>



    <!-- 分享结果 -->

    <div v-else class="cd-share-result">

      <div class="cd-share-success">

        <el-icon :size="32" color="var(--cd-success)"><SuccessFilled /></el-icon>

        <p>分享链接已创建</p>

      </div>

      <div class="cd-share-info">

        <div class="cd-info-row">

          <span class="cd-info-label">分享码</span>

          <code class="cd-info-code">{{ result.shareCode }}</code>

        </div>

        <div v-if="result.extractCode" class="cd-info-row">

          <span class="cd-info-label">提取码</span>

          <code class="cd-info-code">{{ result.extractCode }}</code>

        </div>

      </div>

      <div class="cd-link-box">

        <el-input :model-value="fullShareUrl" readonly size="large" />

        <el-button type="primary" size="large" @click="copyLink">

          <el-icon><Check v-if="copied" /><CopyDocument v-else /></el-icon>

          {{ copied ? '已复制' : '复制' }}

        </el-button>

      </div>

    </div>



    <template #footer>

      <el-button v-if="result" @click="close">完成</el-button>

      <template v-else>

        <el-button @click="close">取消</el-button>

        <el-button type="primary" :loading="loading" @click="createShare">

          <el-icon><Share /></el-icon>

          创建分享

        </el-button>

      </template>

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

  max-width: 320px;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

}



/* 分享结果 */

.cd-share-result {

  display: flex;

  flex-direction: column;

  gap: 20px;

  padding: 8px 0;

}



.cd-share-success {

  text-align: center;

  padding: 8px 0;

}



.cd-share-success p {

  margin: 8px 0 0;

  color: var(--cd-text-primary);

  font-weight: 600;

  font-size: 15px;

}



.cd-share-info {

  display: flex;

  flex-direction: column;

  gap: 10px;

  background: var(--cd-bg);

  padding: 14px;

  border-radius: var(--cd-radius);

}



.cd-info-row {

  display: flex;

  align-items: center;

  justify-content: space-between;

}



.cd-info-label {

  font-size: 13px;

  color: var(--cd-text-secondary);

  font-weight: 500;

}



.cd-info-code {

  font-family: 'SF Mono', 'Fira Code', monospace;

  font-size: 13px;

  padding: 4px 10px;

  background: var(--cd-bg-white);

  border: 1px solid var(--cd-border);

  border-radius: var(--cd-radius-sm);

  font-weight: 600;

  color: var(--cd-primary);

}



.cd-link-box {

  display: flex;

  gap: 8px;

}

</style>
