<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { request } from '@/api/http'
import MobileEmailCodeBtn from '@/components/MobileEmailCodeBtn.vue'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  (e: 'update:show', val: boolean): void
  (e: 'success'): void
}>()

const auth = useAuthStore()
const newEmailInput = ref('')
const emailCodeInput = ref('')
const saving = ref(false)

const isBound = computed(() => Boolean(auth.email))
const titleText = computed(() => (isBound.value ? '更改电子邮箱' : '绑定电子邮箱'))
const descText = computed(() => (isBound.value ? '修改电子邮箱需向新邮箱发送验证码完成校验' : '首次绑定电子邮箱需获取并输入验证码'))
const submitText = computed(() => (isBound.value ? '确认更改' : '确认绑定'))

watch(() => props.show, (val) => {
  if (val) {
    newEmailInput.value = ''
    emailCodeInput.value = ''
  }
})

function close() {
  emit('update:show', false)
}

async function submit() {
  const em = newEmailInput.value.trim()
  if (!em) {
    uni.showToast({ title: isBound.value ? '请输入新电子邮箱' : '请输入电子邮箱', icon: 'none' })
    return
  }
  if (isBound.value && em === auth.email) {
    uni.showToast({ title: '新邮箱不能与原邮箱相同', icon: 'none' })
    return
  }
  if (!em.match(/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/)) {
    uni.showToast({ title: '邮箱格式不正确', icon: 'none' })
    return
  }
  if (!emailCodeInput.value.trim()) {
    uni.showToast({ title: '请输入 6 位邮箱验证码', icon: 'none' })
    return
  }

  saving.value = true
  try {
    await request({
      url: '/api/auth/profile',
      method: 'PUT',
      data: {
        email: em,
        emailCode: emailCodeInput.value.trim()
      }
    })
    await auth.fetchProfile()
    uni.showToast({ title: isBound.value ? '邮箱修改成功' : '邮箱绑定成功', icon: 'none' })
    emit('success')
    close()
  } catch (err: any) {
    uni.showToast({ title: err?.message || '设置失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <view v-if="show" class="about-root" @touchmove.stop.prevent>
    <view class="about-mask" @click="close" />
    <view class="about-panel cd-scale-in" style="padding: 40rpx 36rpx; width: 620rpx;" @click.stop>
      <text class="about-name" style="font-size: 34rpx; text-align: left; margin-bottom: 8rpx;">{{ titleText }}</text>
      <text class="about-desc" style="font-size: 24rpx; text-align: left; margin-bottom: 30rpx; color: var(--cd-text-muted);">
        {{ descText }}
      </text>

      <view class="apply-form">
        <!-- 原电子邮箱（仅在已绑定状态下显示） -->
        <view v-if="isBound" class="form-item">
          <text class="form-label">原电子邮箱</text>
          <view class="input-wrap">
            <input
              :value="auth.email"
              disabled
              class="apply-input"
              style="color: var(--cd-text-muted); background: #f1f5f9; cursor: not-allowed;"
            />
          </view>
        </view>

        <!-- 新电子邮箱 -->
        <view class="form-item" :style="{ marginTop: isBound ? '20rpx' : '0' }">
          <text class="form-label">{{ isBound ? '新电子邮箱' : '电子邮箱' }}</text>
          <view class="input-wrap">
            <input
              v-model="newEmailInput"
              :placeholder="isBound ? '请输入新电子邮箱' : '请输入电子邮箱'"
              class="apply-input"
            />
          </view>
        </view>

        <!-- 验证码 -->
        <view class="form-item" style="margin-top: 20rpx;">
          <text class="form-label">{{ isBound ? '新邮箱验证码' : '邮箱验证码' }}</text>
          <view class="input-wrap" style="display: flex; gap: 12rpx; align-items: center;">
            <input
              v-model="emailCodeInput"
              placeholder="6 位验证码"
              class="apply-input"
              maxlength="6"
              style="flex: 1;"
            />
            <MobileEmailCodeBtn :email="newEmailInput.trim()" scene="bind" />
          </view>
        </view>
      </view>

      <view class="apply-buttons" style="margin-top: 36rpx;">
        <view class="btn-cancel cd-pressable" @click="close">
          <text>取消</text>
        </view>
        <view class="btn-submit cd-pressable" :class="{ loading: saving }" @click="submit">
          <text>{{ saving ? (isBound ? '修改中...' : '绑定中...') : submitText }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
@import '@/styles/profile-dialog.scss';
</style>
