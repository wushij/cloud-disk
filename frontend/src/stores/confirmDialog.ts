import { ref } from 'vue'
import { defineStore } from 'pinia'

export interface ConfirmDialogOptions {
  title: string
  message?: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}

export const useConfirmDialogStore = defineStore('confirmDialog', () => {
  const visible = ref(false)
  const title = ref('')
  const message = ref('')
  const confirmText = ref('确定')
  const cancelText = ref('取消')
  const danger = ref(false)

  let resolver: ((result: boolean) => void) | null = null

  function open(options: ConfirmDialogOptions): Promise<boolean> {
    title.value = options.title
    message.value = options.message ?? ''
    confirmText.value = options.confirmText ?? '确定'
    cancelText.value = options.cancelText ?? '取消'
    danger.value = options.danger ?? false
    visible.value = true

    return new Promise((resolve) => {
      resolver = resolve
    })
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
    open,
    confirm,
    cancel
  }
})
