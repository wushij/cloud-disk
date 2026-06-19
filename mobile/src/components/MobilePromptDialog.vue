<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'

const props = withDefaults(
  defineProps<{
    show: boolean
    title: string
    placeholder?: string
    confirmText?: string
    cancelText?: string
    maxlength?: number
    /** 打开时预填内容（如重命名） */
    initialValue?: string
    /** 为 true 时选中主文件名，不含最后一个后缀 */
    selectStem?: boolean
  }>(),
  {
    placeholder: '请输入',
    confirmText: '确定',
    cancelText: '取消',
    maxlength: 32,
    initialValue: '',
    selectStem: false
  }
)

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void
  (e: 'confirm', value: string): void
  (e: 'cancel'): void
}>()

const inputValue = ref('')
const focused = ref(false)
const selectionStart = ref(-1)
const selectionEnd = ref(-1)

function stemEnd(name: string) {
  const dot = name.lastIndexOf('.')
  return dot > 0 ? dot : name.length
}

function applySelection() {
  const val = inputValue.value
  if (!val) {
    selectionStart.value = -1
    selectionEnd.value = -1
    return
  }
  if (props.selectStem) {
    selectionStart.value = 0
    selectionEnd.value = stemEnd(val)
    return
  }
  if (props.initialValue) {
    selectionStart.value = 0
    selectionEnd.value = val.length
    return
  }
  selectionStart.value = -1
  selectionEnd.value = -1
}

watch(
  () => props.show,
  (visible) => {
    if (!visible) return
    inputValue.value = props.initialValue ?? ''
    applySelection()
  }
)

function close() {
  emit('update:show', false)
}

function onCancel() {
  emit('cancel')
  close()
}

function onConfirm() {
  emit('confirm', inputValue.value.trim())
}

function onInputFocus(ev: FocusEvent) {
  focused.value = true
  applySelection()
  // #ifdef H5
  nextTick(() => {
    const el = ev.target as HTMLInputElement | null
    if (!el || selectionEnd.value < 0) return
    try {
      el.setSelectionRange(selectionStart.value, selectionEnd.value)
    } catch {
      /* ignore */
    }
  })
  // #endif
}
</script>

<template>
  <view v-if="show" class="dialog-root" @touchmove.stop.prevent>
    <view class="dialog-mask" @click="onCancel" />
    <view class="dialog-panel cd-scale-in" @click.stop>
      <text class="dialog-title">{{ title }}</text>
      <view v-if="$slots.desc" class="dialog-desc">
        <slot name="desc" />
      </view>

      <view class="dialog-field" :class="{ focused }">
        <input
          v-model="inputValue"
          class="dialog-input"
          type="text"
          :placeholder="placeholder"
          placeholder-class="dialog-ph"
          :maxlength="maxlength"
          :focus="show"
          :selection-start="selectionStart"
          :selection-end="selectionEnd"
          confirm-type="done"
          @focus="onInputFocus"
          @blur="focused = false"
          @confirm="onConfirm"
        />
      </view>

      <view class="dialog-actions">
        <view class="dialog-btn dialog-btn--ghost cd-pressable" @click="onCancel">
          <text>{{ cancelText }}</text>
        </view>
        <view class="dialog-btn dialog-btn--primary cd-pressable" @click="onConfirm">
          <text>{{ confirmText }}</text>
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
  padding: 40rpx 36rpx 32rpx;
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

.dialog-desc {
  display: block;
  margin-top: 12rpx;
  text-align: center;
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--cd-text-muted);
}

.dialog-field {
  margin-top: 32rpx;
  height: 96rpx;
  padding: 0 28rpx;
  border-radius: 22rpx;
  background: #f4f7fb;
  border: 2rpx solid #e8eef6;
  display: flex;
  align-items: center;
  transition: all 0.2s;

  &.focused {
    background: #fff;
    border-color: #94b4d8;
    box-shadow: 0 0 0 6rpx rgba(100, 150, 210, 0.12);
  }
}

.dialog-input {
  flex: 1;
  height: 96rpx;
  font-size: 30rpx;
  color: var(--cd-text);
  font-weight: 500;
}

.dialog-ph {
  color: var(--cd-text-placeholder);
  font-size: 28rpx;
}

.dialog-actions {
  margin-top: 32rpx;
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
}

.dialog-btn--primary:active {
  transform: scale(0.98);
  box-shadow: 0 6rpx 16rpx rgba(1, 7, 16, 0.16);
}
</style>
