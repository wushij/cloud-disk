<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { request } from '@/api/http'
import { buildPublicShareUrl } from '@/utils/shareUrl'

const props = defineProps<{
  show: boolean
  fileId?: number | null
  folderId?: number | null
  itemName?: string
}>()

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void
  (e: 'cancel'): void
}>()

const extractCode = ref('')
const expireHours = ref<number | null>(24)
const result = ref<{ shareUrl?: string; shareCode?: string; extractCode?: string } | null>(null)
const fullShareUrl = ref('')
const loading = ref(false)

const expirationOptions = [
  { label: '1 小时', value: 1 },
  { label: '24 小时', value: 24 },
  { label: '7 天', value: 168 },
  { label: '永久', value: null }
]

watch(
  () => props.show,
  (visible) => {
    if (visible) {
      extractCode.value = ''
      expireHours.value = 24
      result.value = null
      fullShareUrl.value = ''
    }
  }
)

function close() {
  emit('update:show', false)
}

function onCancel() {
  emit('cancel')
  close()
}

async function createShare() {
  if (!props.fileId && !props.folderId) return
  loading.value = true
  try {
    const body: Record<string, any> = {
      extractCode: extractCode.value.trim() || undefined,
      expireHours: expireHours.value || undefined
    }
    if (props.folderId) {
      body.folderId = props.folderId
    } else {
      body.fileId = props.fileId
    }
    const data = await request<any>({
      url: '/api/share',
      method: 'POST',
      data: body
    })
    result.value = data
    fullShareUrl.value = buildPublicShareUrl(data.shareCode, data.shareUrl)
    uni.showToast({ title: '分享创建成功', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '创建分享失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function copyLink() {
  if (!fullShareUrl.value) return
  uni.setClipboardData({
    data: fullShareUrl.value,
    success: () => {
      uni.showToast({ title: '链接已复制', icon: 'success' })
      close()
    }
  })
}
</script>

<template>
  <view v-if="show" class="dialog-root" @touchmove.stop.prevent>
    <view class="dialog-mask" @click="onCancel" />
    <view class="dialog-panel cd-scale-in" @click.stop>
      <text class="dialog-title">分享{{ folderId ? '文件夹' : '文件' }}</text>
      <text class="dialog-subtitle">{{ itemName }}</text>

      <view v-if="!result" class="dialog-form">
        <!-- 提取码输入 -->
        <text class="form-label">提取码</text>
        <view class="dialog-field">
          <input
            v-model="extractCode"
            class="dialog-input"
            type="text"
            placeholder="留空为公开分享"
            maxlength="16"
            placeholder-class="dialog-ph"
          />
        </view>

        <!-- 有效期选择 -->
        <text class="form-label expiration-label">有效期</text>
        <view class="expiration-grid">
          <view
            v-for="opt in expirationOptions"
            :key="String(opt.value)"
            class="expiration-chip cd-pressable"
            :class="{ active: expireHours === opt.value }"
            @click="expireHours = opt.value"
          >
            <text class="chip-text">{{ opt.label }}</text>
          </view>
        </view>

        <!-- 操作按钮 -->
        <view class="dialog-actions">
          <view class="dialog-btn dialog-btn--ghost cd-pressable" @click="onCancel">
            <text>取消</text>
          </view>
          <view class="dialog-btn dialog-btn--primary cd-pressable" :class="{ 'is-loading': loading }" @click="createShare">
            <text>{{ loading ? '创建中...' : '创建分享' }}</text>
          </view>
        </view>
      </view>

      <view v-else class="dialog-result">
        <view class="success-header">
          <view class="success-icon">
            <u-icon name="checkmark-circle-fill" size="36" color="var(--cd-success)" />
          </view>
          <text class="success-title">分享链接已创建</text>
          <text class="success-desc">复制链接后即可分享给他人</text>
        </view>

        <view class="share-info-card">
          <view class="info-row">
            <text class="info-label">分享码</text>
            <text class="info-code">{{ result.shareCode }}</text>
          </view>
          <view v-if="result.extractCode" class="info-row">
            <text class="info-label">提取码</text>
            <text class="info-code">{{ result.extractCode }}</text>
          </view>
        </view>

        <view class="url-copy-box">
          <scroll-view scroll-x class="url-scroll">
            <text class="url-text">{{ fullShareUrl }}</text>
          </scroll-view>
        </view>

        <!-- 操作按钮 -->
        <view class="dialog-actions result-actions">
          <view class="dialog-btn dialog-btn--ghost cd-pressable" @click="close">
            <text>关闭</text>
          </view>
          <view class="dialog-btn dialog-btn--primary cd-pressable" @click="copyLink">
            <text>复制并关闭</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.dialog-root {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
}

.dialog-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(6rpx);
}

.dialog-panel {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 620rpx;
  padding: 44rpx 36rpx 32rpx;
  background: var(--cd-bg-card);
  border-radius: 32rpx;
  box-shadow:
    0 24rpx 64rpx rgba(15, 23, 42, 0.18),
    0 0 0 1rpx rgba(255, 255, 255, 0.6) inset;
  border: 1rpx solid var(--cd-border-light);
}

.dialog-title {
  display: block;
  text-align: center;
  font-size: 34rpx;
  font-weight: 800;
  color: var(--cd-text);
  letter-spacing: -0.5rpx;
}

.dialog-subtitle {
  display: block;
  margin-top: 8rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--cd-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dialog-form {
  margin-top: 36rpx;
}

.form-label {
  display: block;
  font-size: 24rpx;
  font-weight: 700;
  color: var(--cd-text-secondary);
  margin-bottom: 12rpx;
}

.expiration-label {
  margin-top: 28rpx;
}

.dialog-field {
  height: 88rpx;
  padding: 0 24rpx;
  border-radius: 20rpx;
  background: #f4f7fb;
  border: 2rpx solid #e8eef6;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.dialog-input {
  flex: 1;
  height: 88rpx;
  font-size: 28rpx;
  color: var(--cd-text);
}

.dialog-ph {
  color: var(--cd-text-placeholder);
  font-size: 26rpx;
}

.expiration-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12rpx;
}

.expiration-chip {
  height: 72rpx;
  border-radius: 16rpx;
  background: #f1f5f9;
  border: 1rpx solid var(--cd-border-light);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--cd-transition-fast);

  .chip-text {
    font-size: 24rpx;
    color: var(--cd-text-secondary);
    font-weight: 600;
  }

  &.active {
    background: var(--cd-primary-bg);
    border-color: var(--cd-primary-light);
    .chip-text {
      color: var(--cd-primary);
      font-weight: 700;
    }
  }
}

.dialog-actions {
  margin-top: 40rpx;
  display: flex;
  gap: 16rpx;
}

.dialog-btn {
  flex: 1;
  height: 88rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  transition: all var(--cd-transition-fast);
}

.dialog-btn--ghost {
  background: #f1f5f9;
  color: var(--cd-text-secondary);
  border: 1rpx solid var(--cd-border);
}

.dialog-btn--ghost:active {
  background: #e2e8f0;
}

.dialog-btn--primary {
  background: var(--cd-primary-gradient);
  color: #fff;
  box-shadow: 0 10rpx 28rpx rgba(1, 7, 16, 0.2);

  &.is-loading {
    opacity: 0.8;
    pointer-events: none;
  }
}

.dialog-btn--primary:active {
  transform: scale(0.98);
}

/* ============ 分享结果 ============ */
.dialog-result {
  margin-top: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.success-header {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24rpx;
}

.success-icon {
  margin-bottom: 12rpx;
}

.success-title {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--cd-text);
}

.success-desc {
  font-size: 22rpx;
  color: var(--cd-text-muted);
  margin-top: 6rpx;
}

.share-info-card {
  background: #f8fafc;
  border: 1rpx solid var(--cd-border-light);
  border-radius: 20rpx;
  padding: 20rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.info-label {
  font-size: 24rpx;
  color: var(--cd-text-secondary);
  font-weight: 500;
}

.info-code {
  font-family: monospace;
  font-size: 24rpx;
  font-weight: 700;
  color: var(--cd-primary);
  background: #fff;
  padding: 6rpx 20rpx;
  border: 1rpx solid var(--cd-border);
  border-radius: 999rpx;
}

.url-copy-box {
  margin-top: 24rpx;
  background: #f1f5f9;
  border-radius: 16rpx;
  padding: 16rpx 20rpx;
  display: flex;
  align-items: center;
}

.url-scroll {
  width: 100%;
  white-space: nowrap;
}

.url-text {
  font-size: 24rpx;
  color: var(--cd-text-secondary);
}

.result-actions {
  margin-top: 32rpx;
}
</style>
