<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { getApiErrorMessage } from '@/utils/error'

const props = defineProps<{
  email: string
  scene: 'login' | 'register' | 'resetpwd' | 'bind'
}>()

const loading = ref(false)
const countdown = ref(0)

async function sendCode() {
  const em = props.email ? props.email.trim() : ''
  if (!em) {
    ElMessage.warning('请先输入有效的邮箱地址')
    return
  }
  if (!em.match(/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }

  loading.value = true
  try {
    await http.post('/api/auth/email/send-code', { email: em, scene: props.scene }, { skipErrorHandler: true })
    ElMessage.success('验证码发送成功，请前往邮箱查看')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (e: unknown) {
    ElMessage.error(getApiErrorMessage(e, '验证码发送失败'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-button
    type="primary"
    plain
    size="large"
    class="send-code-btn"
    :disabled="loading || countdown > 0"
    :loading="loading"
    @click="sendCode"
  >
    {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
  </el-button>
</template>

<style scoped>
.send-code-btn {
  white-space: nowrap;
  min-width: 110px;
  border-radius: 999px !important;
  font-weight: 500 !important;
  transition: all 0.2s ease !important;
}
</style>
