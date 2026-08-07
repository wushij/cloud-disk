<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { request } from '@/api/http'
import { fmtSize } from '@/utils/fileCover'
import BrandMark from '@/components/BrandMark.vue'

const props = defineProps<{
  show: boolean
  quotaBytes?: number
}>()

const emit = defineEmits<{
  (e: 'update:show', val: boolean): void
  (e: 'success'): void
}>()

const auth = useAuthStore()
const applyGB = ref('')
const applyReason = ref('')
const applySaving = ref(false)

function close() {
  emit('update:show', false)
}

async function submitApply() {
  let targetBytes: number
  if (auth.isAdmin) {
    if (!applyGB.value) {
      uni.showToast({ title: '请输入目标容量', icon: 'none' })
      return
    }
    const gb = Number(applyGB.value)
    if (isNaN(gb) || gb <= 0) {
      uni.showToast({ title: '容量必须大于 0', icon: 'none' })
      return
    }
    targetBytes = Math.round(gb * 1024 * 1024 * 1024)
  } else {
    targetBytes = 500 * 1024 * 1024 * 1024
  }

  if (props.quotaBytes != null && targetBytes <= props.quotaBytes) {
    uni.showToast({ title: '申请配额必须大于当前配额', icon: 'none' })
    return
  }

  applySaving.value = true
  try {
    await request({
      url: '/api/quota-applications',
      method: 'POST',
      data: {
        applyQuota: targetBytes,
        reason: applyReason.value
      }
    })
    uni.showToast({ title: '申请已提交', icon: 'none' })
    applyGB.value = ''
    applyReason.value = ''
    emit('success')
    close()
  } catch (err: any) {
    uni.showToast({ title: err.response?.data?.message || err.message || '提交失败', icon: 'none' })
  } finally {
    applySaving.value = false
  }
}
</script>

<template>
  <view v-if="show" class="about-root" @touchmove.stop.prevent>
    <view class="about-mask" @click="close" />
    <view class="about-panel cd-scale-in apply-panel" @click.stop>
      <view class="apply-header">
        <view class="apply-icon">
          <BrandMark size="40rpx" />
        </view>
        <text class="apply-title">申请容量扩容</text>
      </view>
      
      <view class="apply-hint">
        <text>请填写申请的目标容量（GB）及扩容原因</text>
      </view>

      <view class="apply-form">
        <view v-if="auth.isAdmin" class="form-item">
          <text class="form-label">
            目标容量 (GB)
            <text v-if="quotaBytes" class="form-label-tip">
              （当前 {{ fmtSize(quotaBytes) }}）
            </text>
          </text>
          <view class="input-wrap">
            <input
              type="number"
              v-model="applyGB"
              placeholder="例如 5"
              class="apply-input"
            />
            <text class="input-unit">GB</text>
          </view>
        </view>
        <view v-else class="form-item">
          <text class="form-label">目标容量</text>
          <view class="apply-fixed-quota">
            <text>500 GB</text>
          </view>
        </view>

        <view class="form-item" style="margin-top: 20rpx;">
          <text class="form-label">申请原因</text>
          <textarea
            v-model="applyReason"
            placeholder="请输入申请扩容的理由..."
            class="apply-textarea"
            maxlength="200"
          />
        </view>
      </view>

      <view class="apply-buttons">
        <view class="btn-cancel cd-pressable" @click="close">
          <text>取消</text>
        </view>
        <view class="btn-submit cd-pressable" :class="{ loading: applySaving }" @click="submitApply">
          <text>{{ applySaving ? '提交中...' : '提交申请' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
@import '@/styles/profile-dialog.scss';
</style>
