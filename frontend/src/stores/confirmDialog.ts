import { ref } from 'vue'
import { defineStore } from 'pinia'

export interface ConfirmDialogOptions {
  title: string
  message?: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
  /** 仅确认按钮（提示框模式） */
  alertOnly?: boolean
  /** 图标风格 */
  tone?: 'info' | 'warning' | 'success' | 'danger'
}

export const useConfirmDialogStore = defineStore('confirmDialog', () => {
  const visible = ref(false)
  const title = ref('')
  const message = ref('')
  const confirmText = ref('确定')
  const cancelText = ref('取消')
  const danger = ref(false)
  const alertOnly = ref(false)
  const tone = ref<ConfirmDialogOptions['tone'] | null>(null)

  let resolver: ((result: boolean) => void) | null = null

  function open(options: ConfirmDialogOptions): Promise<boolean> {
    title.value = options.title
    message.value = options.message ?? ''
    confirmText.value = options.confirmText ?? '确定'
    cancelText.value = options.cancelText ?? '取消'
    danger.value = options.danger ?? false
    alertOnly.value = options.alertOnly ?? false
    tone.value = options.tone ?? (options.danger ? 'danger' : (options.alertOnly ? 'info' : null))
    visible.value = true

    return new Promise((resolve) => {
      resolver = resolve
    })
  }

  function openAlert(options: Omit<ConfirmDialogOptions, 'alertOnly' | 'cancelText' | 'danger'> & { tone?: ConfirmDialogOptions['tone'] }): Promise<void> {
    return open({
      ...options,
      alertOnly: true,
      cancelText: '',
      danger: false,
      tone: options.tone ?? 'info'
    }).then(() => undefined)
  }

  function finish(result: boolean) {
    visible.value = false
    resolver?.(result)
    resolver = null
  }

  function confirm() {
    finish(true)
  }

  function cancel() {
    finish(false)
  }

  return {
    visible,
    title,
    message,
    confirmText,
    cancelText,
    danger,
    alertOnly,
    tone,
    open,
    openAlert,
    confirm,
    cancel
  }
})
