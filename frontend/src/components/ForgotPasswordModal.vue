<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Message, Key, Lock } from '@element-plus/icons-vue'
import http from '@/api/http'
import { getApiErrorMessage } from '@/utils/error'
import EmailCodeBtn from './EmailCodeBtn.vue'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'success', payload: { email: string; newPassword: string }): void
}>()

const form = ref({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})
const loading = ref(false)

watch(() => props.modelValue, (val) => {
  if (val) {
    // 每次打开重置密码弹窗时，强制清空表单，规避浏览器自动填充
    form.value = { email: '', code: '', newPassword: '', confirmPassword: '' }
  }
})

function close() {
  emit('update:modelValue', false)
}

async function handleReset() {
  const f = form.value
  if (!f.email.trim() || !f.code.trim() || !f.newPassword) {
    ElMessage.warning('请完整填写所有必填字段')
    return
  }
  if (f.newPassword.length < 6) {
    ElMessage.warning('新密码长度不能少于 6 位')
    return
  }
  if (f.newPassword !== f.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    await http.post('/api/auth/email/reset-password', {
      email: f.email.trim(),
      code: f.code.trim(),
      newPassword: f.newPassword
    }, { skipErrorHandler: true })

    ElMessage.success('密码重置成功！请使用新密码登录')
    emit('success', { email: f.email.trim(), newPassword: f.newPassword })
    close()
  } catch (e: unknown) {
    ElMessage.error(getApiErrorMessage(e, '重置密码失败'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="🔒 重置 / 找回密码"
    width="540px"
    class="forgot-dialog"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="forgot-dialog-content">
      <p class="forgot-desc">请输入注册时绑定的电子邮箱，获取验证码后重置登录密码。</p>

      <el-form label-position="top" class="forgot-form" autocomplete="off" @submit.prevent="handleReset">
        <!-- 隐藏 Dummy 占位输入框，防止 Chrome/Edge 浏览器智能探测并自动填充账号密码 -->
        <input type="text" style="display:none" autocomplete="username" />
        <input type="password" style="display:none" autocomplete="current-password" />

        <!-- 电子邮箱与获取验证码按钮同行置放 -->
        <el-form-item label="电子邮箱">
          <div class="code-input-group">
            <el-input
              v-model="form.email"
              placeholder="注册填写的电子邮箱"
              size="large"
              autocomplete="email"
              :prefix-icon="Message"
            />
            <EmailCodeBtn :email="form.email" scene="resetpwd" />
          </div>
        </el-form-item>

        <!-- 验证码单独整行 -->
        <el-form-item label="邮箱验证码">
          <el-input
            v-model="form.code"
            placeholder="请输入发送至邮箱的 6 位数字验证码"
            size="large"
            maxlength="6"
            autocomplete="one-time-code"
            :prefix-icon="Key"
          />
        </el-form-item>

        <!-- 新密码与确认新密码双列布局 -->
        <div class="pwd-grid">
          <el-form-item label="新密码">
            <el-input
              v-model="form.newPassword"
              type="password"
              placeholder="输入 6-64 位新密码"
              size="large"
              autocomplete="new-password"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item label="确认新密码">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="再次输入新密码"
              size="large"
              autocomplete="new-password"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
        </div>
      </el-form>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button size="large" @click="close">取 消</el-button>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          @click="handleReset"
        >
          确认重置密码
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.forgot-dialog-content {
  padding: 8px 0;
}
.forgot-desc {
  margin: 0 0 18px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}
.code-input-group {
  display: flex;
  gap: 12px;
  width: 100%;
}
.pwd-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
@media (max-width: 540px) {
  .pwd-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
:deep(.el-input__wrapper) {
  border-radius: 999px !important;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
.dialog-footer .el-button {
  border-radius: 999px !important;
  padding: 0 24px;
}
</style>
